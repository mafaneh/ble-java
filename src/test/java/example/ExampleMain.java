package example;

import it.tangodev.ble.*;
import it.tangodev.ble.BleCharacteristic.CharacteristicFlag;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.ArrayList;
import java.util.List;

public class ExampleMain {
	public static final String DESCRIPTOR_UUID = "b4a20bb9-d3c6-4086-94ed-7759ec9d64ba";

	protected String valueString = "Ciao ciao";
	BleApplication app;
	BleService service;
	BleCharacteristic characteristic;

	public void notifyBle(String value) {
		this.valueString = value;
		characteristic.sendNotification(null);
	}
	
	public ExampleMain() throws DBusException, InterruptedException {

		BleApplicationListener appListener = new BleApplicationListener() {
			@Override
			public void deviceDisconnected(String path) {
				System.out.println("Device disconnected: " + path);
			}
			
			@Override
			public void deviceConnected(String path, String address) {
				System.out.println("Device connected: " + path + " ADDR: " + address);
			}
		};
		app = new BleApplication("/tango", appListener);
		service = new BleService("/tango/s", "13333333-3333-3333-3333-333333333001", true);
		List<CharacteristicFlag> flags = new ArrayList<CharacteristicFlag>();
		flags.add(CharacteristicFlag.READ);
		flags.add(CharacteristicFlag.WRITE);
		flags.add(CharacteristicFlag.NOTIFY);
		
		characteristic = new BleCharacteristic("/tango/s/c", service, flags, "13333333-3333-3333-3333-333333333002", new BleCharacteristicListener() {
			@Override
			public void setValue(String devicePath, int offset, byte[] value) {
				try {
					valueString = new String(value, "UTF8");
				} catch(Exception e) {
					System.out.println("");
				}
			}
			
			@Override
			public byte[] getValue(String devicePath) {
				try {
					return valueString.getBytes("UTF8");
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
			}
		});

		BleDescriptor.DescriptorFlag[] descriptorFlags = {
				BleDescriptor.DescriptorFlag.READ, BleDescriptor.DescriptorFlag.WRITE
		};

		BleDescriptor descriptor = new BleDescriptor("/tango/s/c/d", characteristic, descriptorFlags,
				DESCRIPTOR_UUID);
		descriptor.setValue("fluffy".getBytes());
		characteristic.addDescriptor(descriptor);

//		cccd = new ClientCharacteristicConfigurationDescriptor("/tango/s/c/cccd", characteristic);
//		characteristic.addDescriptor(cccd);

		service.addCharacteristic(characteristic);
		app.addService(service);
		
		ExampleCharacteristic exampleCharacteristic = new ExampleCharacteristic(service);
		service.addCharacteristic(exampleCharacteristic);
		app.start();
		System.out.println("Listening on adapter " + app.getBleAdapter().getAddress() + " path: " + app.getBleAdapter().getPath());
	}

	private void simulateActivity(ExampleMain example) throws InterruptedException {
		Thread.sleep(15000);
		int i = 0;
		while (true) {
			example.notifyBle("woooooo " + i++);
			Thread.sleep(15000);
		}
	}

	public static void main(String[] args) throws DBusException, InterruptedException {
		ExampleMain example = new ExampleMain();
		System.out.println("Started");
		example.simulateActivity(example);
		return;
	}

}
