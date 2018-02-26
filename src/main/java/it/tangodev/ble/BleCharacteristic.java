package it.tangodev.ble;

import it.tangodev.utils.Utils;
import org.bluez.GattCharacteristic1;
import org.dbus.PropertiesChangedSignal.PropertiesChanged;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static it.tangodev.ble.GattPropertyKeys.*;

/**
 * BleCharacteristic represent a single peripheral's value that can be read, write or notified.
 * @author Tongo
 *
 */
//@SuppressWarnings("rawtypes")
public class BleCharacteristic implements GattCharacteristic1, Properties {
	private static final Logger LOG = LoggerFactory.getLogger(BleCharacteristic.class);

	private static final String GATT_CHARACTERISTIC_INTERFACE = "org.bluez.GattCharacteristic1";

	private BleService service = null;
	protected String uuid = null;
	private List<String> flags = new ArrayList<String>();;
	protected String path = null;
	private boolean isNotifying = false;
	protected BleCharacteristicListener listener;
	private Map<String, BleDescriptor> descriptors = new HashMap<>();

	/**
	 * A flag indicate the operation allowed on a single characteristic.
	 * @author Tongo
	 *
	 */
	public enum CharacteristicFlag {
		READ("read"),
		WRITE("write"),
		NOTIFY("notify");
		
		private String flag;
		
		CharacteristicFlag(String flag) {
			this.flag = flag;
		}
		
		public static CharacteristicFlag fromString(String flag) {
			for (CharacteristicFlag t : CharacteristicFlag.values()) {
				if (flag.equalsIgnoreCase(t.flag)) { return t; }
			}
			throw new RuntimeException("Specified Characteristic Flag not valid [" + flag + "]");
		}
		
		@Override
		public String toString() {
			return this.flag;
		}
	}

	public BleCharacteristic() {
	}

	/**
	 * 
	 * @param service: The service that contains the Characteristic
	 */
	public BleCharacteristic(BleService service) {
		this.service = service;
	}
	
	/**
	 * 
	 * @param path: The absolute path, APPLICATION/SERVICE/CHARACTERISTIC
	 * @param service
	 * @param flags
	 * @param uuId
	 * @param listener: who can provide the data
	 */
	public BleCharacteristic(String path, BleService service, List<CharacteristicFlag> flags, String uuId, BleCharacteristicListener listener) {
		this.path = path;
		this.service = service;
		this.uuid = uuId;
		setFlags(flags);
		this.listener = listener;
	}

	public void setFlags(List<CharacteristicFlag> flags) {
		for (CharacteristicFlag characteristicFlag : flags) {
			this.flags.add(characteristicFlag.toString());
		}
	}
	
	protected void export(DBusConnection dbusConnection) throws DBusException {
		LOG.debug("export");
		dbusConnection.exportObject(this.getPath().toString(), this);
	}
	
	/**
	 * Return the Path (dbus class)
	 * @return
	 */
	public Path getPath() {
		return new Path(path);
	}
	
	public Map<String, Map<String, Variant>> getProperties() {
		LOG.debug("gatProperties");

		Map<String, Variant> characteristicMap = new HashMap<String, Variant>();
		
		Variant<Path> servicePathProperty = new Variant<Path>(service.getPath());
		characteristicMap.put(SERVICE_PROPERTY_KEY, servicePathProperty);
		
		Variant<String> uuidProperty = new Variant<String>(this.uuid);
		characteristicMap.put(UUID_PROPERTY_KEY, uuidProperty);
		
		Variant<String[]> flagsProperty = new Variant<String[]>(Utils.getStringArrayFromList(this.flags));
		characteristicMap.put(FLAGS_PROPERTY_KEY, flagsProperty);
		
		Variant<Path[]> descriptorsPathProperty = new Variant<Path[]>(getDescriptorPaths());
		characteristicMap.put(DESCRIPTORS_PROPERTY_KEY, descriptorsPathProperty);
		
		Map<String, Map<String, Variant>> externalMap = new HashMap<String, Map<String, Variant>>();
		externalMap.put(GATT_CHARACTERISTIC_INTERFACE, characteristicMap);
		
		return externalMap;
	}

	public Path[] getDescriptorPaths() {
		Path[] descriptorPaths = new Path[descriptors.size()];
		int i =0;
		for (String pathString : descriptors.keySet()) {
			descriptorPaths[i] = new Path(pathString);
			i++;
		}
		return descriptorPaths;
	}

	/**
	 * Call this method to send a notification to a central.
	 */
	public void sendNotification(String devicePath) {
		try {
			DBusConnection dbusConnection = DBusConnection.getConnection(DBusConnection.SYSTEM);
			
			Variant<byte[]> signalValueVariant = new Variant<byte[]>(onReadValue(devicePath));
			Map<String, Variant> signalValue = new HashMap<String, Variant>();
			signalValue.put(VALUE_PROPERTY_KEY, signalValueVariant);
			
			PropertiesChanged signal = new PropertiesChanged(this.getPath().toString(), GATT_CHARACTERISTIC_INTERFACE, signalValue, new ArrayList<String>());
			dbusConnection.sendSignal(signal);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean isRemote() {
		return false;
	}

	/**
	 * This method is called when the central request the Characteristic's value.
	 */
	@Override
	public byte[] ReadValue(Map<String, Variant> option) {
		LOG.debug("ReadValue option[" + option + "]");
		int offset = getIntOption(option, "offset");

		String devicePath = null;
		devicePath = getPathOption(option, "device");

		byte[] valueBytes = onReadValue(devicePath);
		byte[] slice = Arrays.copyOfRange(valueBytes, offset, valueBytes.length);
		return slice;
	}

	/**
	 * This method is called when the central want to write the Characteristic's value.
	 */
	@Override
	public void WriteValue(byte[] value, Map<String, Variant> option) {
		LOG.debug("WriteValue " + value.length + "  option[" + option + "]");
		int offset = getIntOption(option, "offset");

		String devicePath = getPathOption(option, "device");
		LOG.debug("WriteValue devicePath = " + devicePath);
		onWriteValue(devicePath, offset, value);
	}

	private int getIntOption(Map<String, Variant> option, String key) {
		int value = 0;
		if(option.containsKey(key)) {
			Variant<UInt16> vvalue = option.get(key);
			value = (vvalue.getValue() != null) ? vvalue.getValue().intValue() : value;
		}
		return value;
	}

	private String getStringOption(Map<String, Variant> option, String key) {
		String value = null;
		if(option.containsKey(key)) {
			Variant<String> vvalue = option.get(key);
			value = (vvalue.getValue() != null) ? vvalue.getValue().toString() : value;
		}
		return value;
	}

	protected String getPathOption(Map<String, Variant> option, String key) {
		String path = null;
		if (option.containsKey(key)) {
			Variant<Path> pathVariant = null;
			pathVariant = option.get(key);
			if (pathVariant != null) path = pathVariant.getValue().getPath();
		}
		return path;
	}

	protected byte[] onReadValue(String devicePath) {
		return listener.getValue(devicePath);
	}

	protected void onWriteValue(String devicePath, int offset, byte[] value) {
		listener.setValue(devicePath, offset, value);
	}

	public void addDescriptor(BleDescriptor descriptor) {
		descriptors.put(descriptor.getPath(), descriptor);
	}

	public Map<String, BleDescriptor> getDescriptors() {
		return descriptors;
	}

	@Override
	public void StartNotify() {
		LOG.debug("StartNotify");
		if(isNotifying) {
			System.out.println("Characteristic already notifying");
			return;
		}
		this.isNotifying = true;
	}

	@Override
	public void StopNotify() {
		LOG.debug("StopNotify");
		if(!isNotifying) {
			System.out.println("Characteristic already not notifying");
			return;
		}
		this.isNotifying = false;
	}
	
	@Override
	public <A> A Get(String interface_name, String property_name) {
		// TODO Auto-generated method stub
		
		return null;
	}

	@Override
	public <A> void Set(String interface_name, String property_name, A value) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public Map<String, Variant> GetAll(String interfaceName) {
		LOG.debug("GetAll " + interfaceName);
		if(GATT_CHARACTERISTIC_INTERFACE.equals(interfaceName)) {
			return this.getProperties().get(GATT_CHARACTERISTIC_INTERFACE);
		}
		throw new RuntimeException("Interfaccia sbagliata [interface_name=" + interfaceName + "]");
	}

	public BleService getService() {
		return service;
	}

	public void setService(BleService service) {
		this.service = service;
	}
}
