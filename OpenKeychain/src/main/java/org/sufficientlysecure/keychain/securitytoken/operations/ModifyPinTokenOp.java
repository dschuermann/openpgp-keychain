package org.sufficientlysecure.keychain.securitytoken.operations;


import java.io.IOException;

import org.sufficientlysecure.keychain.securitytoken.CardException;
import org.sufficientlysecure.keychain.securitytoken.CommandApdu;
import org.sufficientlysecure.keychain.securitytoken.ResponseApdu;
import org.sufficientlysecure.keychain.securitytoken.SecurityTokenConnection;
import org.sufficientlysecure.keychain.util.Passphrase;


public class ModifyPinTokenOp {
    private static final int MIN_PW3_LENGTH = 8;

    private final SecurityTokenConnection connection;
    private final Passphrase adminPin;

    public static ModifyPinTokenOp create(SecurityTokenConnection connection, Passphrase adminPin) {
        return new ModifyPinTokenOp(connection, adminPin);
    }

    private ModifyPinTokenOp(SecurityTokenConnection connection,
            Passphrase adminPin) {
        this.connection = connection;
        this.adminPin = adminPin;
    }

    public void modifyPw1andPw3Pins(byte[] newPin, byte[] newAdminPin) throws IOException {
        // Order is important for Gnuk, otherwise it will be set up in "admin less mode".
        // http://www.fsij.org/doc-gnuk/gnuk-passphrase-setting.html#set-up-pw1-pw3-and-reset-code
        modifyPw3Pin(newAdminPin);
        modifyPw1PinWithEffectiveAdminPin(new Passphrase(new String(newAdminPin)), newPin);
    }

    public void modifyPw1Pin(byte[] newPin) throws IOException {
        modifyPw1PinWithEffectiveAdminPin(adminPin, newPin);
    }

    private void modifyPw1PinWithEffectiveAdminPin(Passphrase effectiveAdminPin, byte[] newPin) throws IOException {
        connection.verifyAdminPin(effectiveAdminPin);

        int maxPw1Length = connection.getOpenPgpCapabilities().getPw3MaxLength();
        if (newPin.length < 6 || newPin.length > maxPw1Length) {
            throw new IOException("Invalid PIN length");
        }

        CommandApdu changePin = connection.getCommandFactory().createResetPw1Command(newPin);
        ResponseApdu response = connection.communicate(changePin);

        if (!response.isSuccess()) {
            throw new CardException("Failed to change PIN", response.getSw());
        }
    }

    /**
     * Modifies the user's PW3. Before sending, the new PIN will be validated for
     * conformance to the token's requirements for key length.
     */
    private void modifyPw3Pin(byte[] newAdminPin) throws IOException {
        int maxPw3Length = connection.getOpenPgpCapabilities().getPw3MaxLength();

        if (newAdminPin.length < MIN_PW3_LENGTH || newAdminPin.length > maxPw3Length) {
            throw new IOException("Invalid PIN length");
        }

        byte[] pin = adminPin.toStringUnsafe().getBytes();

        CommandApdu changePin = connection.getCommandFactory().createChangePw3Command(pin, newAdminPin);
        ResponseApdu response = connection.communicate(changePin);

        connection.invalidatePw3();

        if (!response.isSuccess()) {
            throw new CardException("Failed to change PIN", response.getSw());
        }
    }
}