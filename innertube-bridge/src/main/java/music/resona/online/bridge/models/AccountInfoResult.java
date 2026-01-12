package music.resona.online.bridge.models;

/**
 * Represents YouTube Music account information.
 */
public class AccountInfoResult {
    public final String name;
    public final String email;
    public final String channelHandle;
    public final String thumbnailUrl;
    
    public AccountInfoResult(String name, String email, String channelHandle, String thumbnailUrl) {
        this.name = name;
        this.email = email;
        this.channelHandle = channelHandle;
        this.thumbnailUrl = thumbnailUrl;
    }
    
    @Override
    public String toString() {
        return name + (email != null ? " (" + email + ")" : "");
    }
}
