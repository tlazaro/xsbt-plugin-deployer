import java.io.ByteArrayInputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 *
 * @author rodrigo
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        PackedApp.loadPack(Launcher.class.getResourceAsStream("/app"));
//        PackedApp.loadPack(new FileInputStream("/home/rodrigo/NetBeansProjects/rolmanager/target/scala_2.8.0/app"));
        PackedApp.SpecialLoader specialLoader = new PackedApp.SpecialLoader();

        Manifest manifest = new Manifest(new ByteArrayInputStream(PackedApp.getEntry("META-INF/MANIFEST.MF")));
        Attributes values = manifest.getMainAttributes();
        String mainClass = values.getValue("Main-Class");
        if (mainClass == null) throw new IllegalStateException("No main class found in app jar");
        Class<?> loadedClass = specialLoader.loadClass(mainClass);
        loadedClass.getMethod("main", String[].class).invoke(null, new Object[]{args});
    }

}
