package org.sufficientlysecure.keychain.ui;


import android.content.Context;
import android.os.Bundle;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.pgp.CanonicalizedSecretKey;
import org.sufficientlysecure.keychain.provider.CachedPublicKeyRing;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.input.RequiredInputParcel;
import org.sufficientlysecure.keychain.ui.base.BaseViewModel;
import org.sufficientlysecure.keychain.ui.dialog.PassphraseUnlockDialog;
import org.sufficientlysecure.keychain.ui.dialog.PinUnlockDialog;
import org.sufficientlysecure.keychain.ui.dialog.UnlockDialog;

public class KeyUnlockActivityWrapperViewModel implements BaseViewModel {
    public static final String EXTRA_SUBKEY_ID = "secret_key_id";
    public static final String EXTRA_REQUIRED_INPUT = "required_input";
    private long mKeyId = Constants.key.none;
    private Context mContext;
    private OnViewModelEventBind mOnViewModelEventBind;

    //Passphrase is the default
    private CanonicalizedSecretKey.SecretKeyType mKeyType = CanonicalizedSecretKey.SecretKeyType.
            PASSPHRASE;

    public interface OnViewModelEventBind {
        void showUnlockDialog(UnlockDialog unlockDialog);
    }

    public KeyUnlockActivityWrapperViewModel(OnViewModelEventBind viewModelEventBind) {
        mOnViewModelEventBind = viewModelEventBind;

        if (mOnViewModelEventBind == null) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void prepareViewModel(Bundle savedInstanceState, Bundle arguments, Context context)
            throws AssertionError {
        mContext = context;
        mKeyId = arguments.getLong(EXTRA_SUBKEY_ID);

        if (!arguments.containsKey(EXTRA_SUBKEY_ID)) {
            RequiredInputParcel requiredInput = arguments.getParcelable(EXTRA_REQUIRED_INPUT);
            if (requiredInput != null) {
                switch (requiredInput.mType) {
                    case PASSPHRASE_SYMMETRIC: {
                        mKeyId = Constants.key.symmetric;
                        break;
                    }
                    case PASSPHRASE: {
                        mKeyId = requiredInput.getSubKeyId();
                        break;
                    }
                    default: {
                        throw new AssertionError("Unsupported required input type!");
                    }
                }
            }
        }

        obtainSecretKeyType();
    }

    /**
     * Obtains the secret key type.
     */
    public void obtainSecretKeyType() {
        // find a master key id for our key
        if (mKeyId != Constants.key.symmetric && mKeyId != Constants.key.none) {
            try {
                long masterKeyId = new ProviderHelper(mContext).getMasterKeyId(mKeyId);
                CachedPublicKeyRing keyRing = new ProviderHelper(mContext).
                        getCachedPublicKeyRing(masterKeyId);

                // get the type of key (from the database)
                mKeyType = keyRing.getSecretKeyType(mKeyId);
                //error handler
            } catch (ProviderHelper.NotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts the unlock operation
     */
    public void startUnlockOperation() {
        switch (mKeyType) {
            case PASSPHRASE: {
                mOnViewModelEventBind.showUnlockDialog(new PassphraseUnlockDialog());
                break;
            }
            case PIN: {
                mOnViewModelEventBind.showUnlockDialog(new PinUnlockDialog());
            }
            break;
            default: {
                throw new AssertionError("Unhandled SecretKeyType (should not happen)");
            }
        }
    }

    @Override
    public void saveViewModelState(Bundle outState) {

    }

    @Override
    public void restoreViewModelState(Bundle savedInstanceState) {

    }

    public CanonicalizedSecretKey.SecretKeyType getSecretKeyType() {
        return mKeyType;
    }

    public long getKeyId() {
        return mKeyId;
    }
}
