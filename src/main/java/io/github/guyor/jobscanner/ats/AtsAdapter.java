package io.github.guyor.jobscanner.ats;

import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.RawJob;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Reads the open jobs of one company from one kind of applicant tracking system. */
public interface AtsAdapter {

    /** greenhouse, lever, ashby or comeet. */
    String ats();

    /** All open jobs, or empty when the board does not exist (HTTP 404). */
    Optional<List<RawJob>> fetchJobs(Company company) throws IOException, InterruptedException;
}
