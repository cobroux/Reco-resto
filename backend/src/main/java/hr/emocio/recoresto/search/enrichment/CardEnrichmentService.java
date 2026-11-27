package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * A photo for one restaurant card, taken from its website (spec 007, data-model.md "State flow"): the declared sharing
 * image when it answers as an image (FR-003), otherwise the largest page image that answers as an image, chosen by
 * {@link LargestImageRule} (FR-004, amendment (a): no language model), otherwise no photo. Never an error (FR-012).
 * <p>
 * The whole work runs as a task on the bounded enrichment executor ({@code card-enrichment.executor-size}, PO-10) and
 * gets {@code card-enrichment.budget-seconds} from the call (FR-011, PO-4 a), queue wait included; at the deadline the
 * task is cancelled with an interrupt, which aborts its HTTP request, and the answer is "no photo" ({@code TIMEOUT}).
 * Every website request also gets the remaining budget as timeout (research R6).
 * <p>
 * One INFO line per enrichment: outcome, duration and website host only, never page content
 * (research R12).
 */
@Service
public class CardEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(CardEnrichmentService.class);

    private record Result(EnrichmentOutcome outcome, CardEnrichment enrichment) {

        static Result none(EnrichmentOutcome outcome) {
            return new Result(outcome, CardEnrichment.none());
        }

        static Result photo(String url, EnrichmentOutcome origin) {
            return new Result(origin, CardEnrichment.photo(url, origin));
        }
    }

    private final SafeWebFetcher fetcher;
    private final WebsitePageReader reader;
    private final LargestImageRule rule;
    private final CardEnrichmentProperties properties;
    private final ExecutorService executor;

    public CardEnrichmentService(SafeWebFetcher fetcher, WebsitePageReader reader, LargestImageRule rule,
            CardEnrichmentProperties properties,
            @Qualifier("cardEnrichmentExecutor") ExecutorService executor) {
        this.fetcher = fetcher;
        this.reader = reader;
        this.rule = rule;
        this.properties = properties;
        this.executor = executor;
    }

    /** The photo of the card, or none. */
    public CardEnrichment enrich(WebsiteAddress website) {
        long start = System.nanoTime();
        Deadline deadline = Deadline.after(Duration.ofSeconds(properties.budgetSeconds()));
        Result result = runWithinBudget(website, deadline);
        log.info("Card enrichment {} in {} ms for {}", result.outcome(),
                Duration.ofNanos(System.nanoTime() - start).toMillis(), website.host());
        return result.enrichment();
    }

    private Result runWithinBudget(WebsiteAddress website, Deadline deadline) {
        Future<Result> task;
        try {
            task = executor.submit(() -> enrichNow(website, deadline));
        } catch (RejectedExecutionException e) {
            // Only when the application stops: no slot will ever be free within the budget.
            return Result.none(EnrichmentOutcome.TIMEOUT);
        }
        try {
            return task.get(Math.max(0, deadline.remaining().toNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            return Result.none(EnrichmentOutcome.TIMEOUT);
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            return Result.none(EnrichmentOutcome.TIMEOUT);
        } catch (ExecutionException e) {
            // A defect, not a website failure: still no error for the user (FR-012).
            log.warn("Card enrichment failed unexpectedly for {}: {}", website.host(),
                    e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
            return Result.none(EnrichmentOutcome.UNREACHABLE);
        }
    }

    private Result enrichNow(WebsiteAddress website, Deadline deadline) {
        FetchedPage fetched;
        try {
            fetched = fetcher.page(website.uri(), deadline);
        } catch (FetchException e) {
            return Result.none(e.outcome());
        }
        WebsitePage page = reader.read(fetched);
        URI sharingImage = uri(page.sharingImage());
        if (sharingImage != null && fetcher.isImage(sharingImage, deadline)) {
            return Result.photo(page.sharingImage(), EnrichmentOutcome.SHARING_IMAGE);
        }
        List<ImageCandidate> toCheck = rule.toCheck(page.candidates());
        if (toCheck.isEmpty()) {
            return Result.none(EnrichmentOutcome.NO_IMAGE);
        }
        for (ImageCandidate candidate : toCheck) {
            if (Thread.currentThread().isInterrupted() || deadline.isSpent()) {
                return Result.none(EnrichmentOutcome.TIMEOUT);
            }
            URI address = uri(candidate.url());
            if (address != null && fetcher.isImage(address, deadline)) {
                return Result.photo(candidate.url(), EnrichmentOutcome.LARGEST_PAGE_IMAGE);
            }
        }
        return Result.none(deadline.isSpent() ? EnrichmentOutcome.TIMEOUT : EnrichmentOutcome.NO_DISPLAYABLE_IMAGE);
    }

    private static URI uri(String address) {
        if (address == null) {
            return null;
        }
        try {
            return new URI(address);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
