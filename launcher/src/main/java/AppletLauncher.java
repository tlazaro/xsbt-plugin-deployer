
import java.io.ByteArrayInputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.swing.JApplet;
import javax.swing.RootPaneContainer;

/**
 *
 * @author rodrigo
 */
public class AppletLauncher extends JApplet {

    JApplet delegate;

    public AppletLauncher() throws Exception, ClassNotFoundException, NoSuchMethodException {
        delegate = this;
        PackedApp.loadPack(Launcher.class.getResourceAsStream("/app"));
//        PackedApp.loadPack(new FileInputStream("/home/rodrigo/NetBeansProjects/rolmanager/target/scala_2.8.0/app"));
        PackedApp.SpecialLoader specialLoader = new PackedApp.SpecialLoader();

        Manifest manifest = new Manifest(new ByteArrayInputStream(PackedApp.getEntry("META-INF/MANIFEST.MF")));
        Attributes values = manifest.getMainAttributes();
        String mainClass = values.getValue("Main-Class");
        Class<?> loadedClass = specialLoader.loadClass(mainClass);
        RootPaneContainer comp = (RootPaneContainer) loadedClass.newInstance();
        setRootPane(comp.getRootPane());
        if (comp instanceof JApplet) delegate = (JApplet) comp;
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }
}
