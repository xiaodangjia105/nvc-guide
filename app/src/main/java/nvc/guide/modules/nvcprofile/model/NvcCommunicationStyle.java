package nvc.guide.modules.nvcprofile.model;

public enum NvcCommunicationStyle {
    ASSERTIVE("果断型"),
    PASSIVE("被动型"),
    AGGRESSIVE("攻击型"),
    PASSIVE_AGGRESSIVE("被动攻击型"),
    NVC("非暴力沟通型");

    private final String displayName;

    NvcCommunicationStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}