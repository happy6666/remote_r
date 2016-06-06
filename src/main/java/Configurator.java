import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by yiwen on 6/6/16.
 */
public abstract class Configurator {
	private static Properties _prop;

	public static void readProperties(String path) {
		_prop = new Properties();
		try {
			_prop.load(new FileInputStream(new File(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String getProperties(String key) {
		return _prop.containsKey(key) ? _prop.getProperty(key) : "";
	}
}
