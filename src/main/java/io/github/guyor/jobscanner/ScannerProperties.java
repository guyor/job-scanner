package io.github.guyor.jobscanner;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under the "scanner." prefix (see application.yml). */
@ConfigurationProperties(prefix = "scanner")
public class ScannerProperties {

    /** scan (scan due companies, then rebuild outputs) or build-feed (only rebuild outputs). */
    private String mode = "scan";
    private Path stateDir = Path.of("state");
    private Path siteDir = Path.of("site");
    private Path configDir = Path.of("config");
    /** Shown in the User-Agent so site owners can reach us. */
    private String contact = "https://github.com/guyor/job-scanner";
    private int budgetSeconds = 360;
    private int recheckMinutes = 60;
    private int recentCompanyDays = 14;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Path getStateDir() { return stateDir; }
    public void setStateDir(Path stateDir) { this.stateDir = stateDir; }
    public Path getSiteDir() { return siteDir; }
    public void setSiteDir(Path siteDir) { this.siteDir = siteDir; }
    public Path getConfigDir() { return configDir; }
    public void setConfigDir(Path configDir) { this.configDir = configDir; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public int getBudgetSeconds() { return budgetSeconds; }
    public void setBudgetSeconds(int budgetSeconds) { this.budgetSeconds = budgetSeconds; }
    public int getRecheckMinutes() { return recheckMinutes; }
    public void setRecheckMinutes(int recheckMinutes) { this.recheckMinutes = recheckMinutes; }
    public int getRecentCompanyDays() { return recentCompanyDays; }
    public void setRecentCompanyDays(int recentCompanyDays) { this.recentCompanyDays = recentCompanyDays; }

    public String userAgent() {
        return "job-scanner/0.1 (+" + contact + ")";
    }
}
