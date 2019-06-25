package example;

import it.tangodev.ble.BleApplication;
import it.tangodev.ble.InvalidSettingException;

import java.io.IOException;

public class SetFastAdvertise {
    public static void main(String... args) {

        try {
            BleApplication.setBleAdvertiseIntervalMin("hci0", 160);
            BleApplication.setBleAdvertiseIntervalMax("hci0", 260);
        } catch (IOException | InvalidSettingException e) {
            System.err.println("Unable to change values " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
}
