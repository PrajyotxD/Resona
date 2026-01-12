package music.resona.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Shares account information (name, avatar) between {@link music.resona.MainActivity}
 * and UI fragments that render the authenticated state.
 */
public class AccountInfoViewModel extends ViewModel {

    public static class AccountInfoState {
        public final String displayName;
        public final String avatarUrl;
        public final boolean authenticated;

        public AccountInfoState(@Nullable String displayName,
                                @Nullable String avatarUrl,
                                boolean authenticated) {
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.authenticated = authenticated;
        }
    }

    private final MutableLiveData<AccountInfoState> accountInfo =
            new MutableLiveData<>(new AccountInfoState("Guest", null, false));

    public LiveData<AccountInfoState> getAccountInfo() {
        return accountInfo;
    }

    public void updateAccountInfo(@Nullable String displayName,
                                  @Nullable String avatarUrl,
                                  boolean authenticated) {
        accountInfo.postValue(new AccountInfoState(displayName, avatarUrl, authenticated));
    }
}
