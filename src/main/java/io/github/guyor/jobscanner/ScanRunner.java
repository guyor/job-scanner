package io.github.guyor.jobscanner;

import io.github.guyor.jobscanner.ats.AshbyAdapter;
import io.github.guyor.jobscanner.ats.AtsAdapter;
import io.github.guyor.jobscanner.ats.ComeetAdapter;
import io.github.guyor.jobscanner.ats.GreenhouseAdapter;
import io.github.guyor.jobscanner.ats.LeverAdapter;
import io.github.guyor.jobscanner.filter.FilterConfig;
import io.github.guyor.jobscanner.filter.LocationFilter;
import io.github.guyor.jobscanner.filter.TitleFilter;
import io.github.guyor.jobscanner.http.HostPacer;
import io.github.guyor.jobscanner.http.PoliteHttp;
import io.github.guyor.jobscanner.scan.CompanySeeds;
import io.github.guyor.jobscanner.scan.FeedBuilder;
import io.github.guyor.jobscanner.scan.ScanService;
import io.github.guyor.jobscanner.state.JsonStore;
import io.github.guyor.jobscanner.state.State;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Entry point of a run: load state, scan due companies, write state and site/feed.json. */
@Component
public class ScanRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanRunner.class);

    private final ScannerProperties props;

    public ScanRunner(ScannerProperties props) {
        this.props = props;
    }

    @Override
    public void run(String... args) throws Exception {
        Clock clock = Clock.systemUTC();
        FilterConfig cfg = FilterConfig.load(props.getConfigDir().resolve("filters.yml"));
        TitleFilter titles = new TitleFilter(cfg);
        State state = State.load(props.getStateDir());
        int seeded = CompanySeeds.apply(state, props.getConfigDir().resolve("companies.yml"));
        if (seeded > 0) {
            log.info("Added {} companies from config/companies.yml", seeded);
        }

        var lastScan = clock.instant();
        if ("scan".equals(props.getMode())) {
            HostPacer pacer = new HostPacer(state.hosts, clock, Thread::sleep, RandomGenerator.getDefault());
            pacer.setBase("boards-api.greenhouse.io", 2_000);
            pacer.setBase("api.lever.co", 2_000);
            pacer.setBase("api.eu.lever.co", 2_000);
            pacer.setBase("api.ashbyhq.com", 2_000);
            pacer.setBase("www.comeet.com", 10_000);
            PoliteHttp http = new PoliteHttp(PoliteHttp.javaTransport(), pacer, props.userAgent());
            List<AtsAdapter> adapters = List.of(new GreenhouseAdapter(http), new LeverAdapter(http),
                    new AshbyAdapter(http), new ComeetAdapter(http));
            ScanService scan = new ScanService(adapters, titles, new LocationFilter(cfg), clock,
                    Duration.ofMinutes(props.getRecheckMinutes()));
            int n = scan.run(state, Duration.ofSeconds(props.getBudgetSeconds()));
            log.info("Checked {} companies; {} tracked, {} jobs known", n, state.companies.size(), state.jobs.size());
            state.save(props.getStateDir());
        }
        FeedBuilder feed = new FeedBuilder(titles, clock, Duration.ofDays(props.getRecentCompanyDays()));
        JsonStore.write(props.getSiteDir().resolve("feed.json"), feed.build(state, lastScan));
    }
}
