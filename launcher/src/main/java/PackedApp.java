
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Unpacker;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author rodrigo
 */
public class PackedApp {

    private static byte[] unpackedApp;
    private static Map<String, byte[]> index = new HashMap<String, byte[]>();

    public static void loadPack(InputStream pack) throws IOException {
        Unpacker unpacker = Pack200.newUnpacker();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(3000 * 1024);
//        unpacker.unpack(new GZIPInputStream(new FileInputStream("/home/rodrigo/NetBeansProjects/rolmanager/target/scala_2.8.0/rol-manager_2.8.0-1.0-snapshot.pack.gz")),
//                new JarOutputStream(byteArrayOutputStream));
//        unpacker.unpack(new java.io.File("/home/rodrigo/NetBeansProjects/rolmanager/target/scala_2.8.0/rol-manager_2.8.0-1.0-snapshot.pack.gz"),
//                new JarOutputStream(byteArrayOutputStream));
        unpacker.unpack(new GZIPInputStream(pack),
                new JarOutputStream(byteArrayOutputStream));
        System.out.println("Unpacking done");
        unpackedApp = byteArrayOutputStream.toByteArray();
        index();
    }

    private static void index() throws IOException {
        System.out.println("Indexing");
        JarInputStream input = getInput();
        JarEntry jarEntry;
        try {
            while ((jarEntry = input.getNextJarEntry()) != null) {
                if (!jarEntry.isDirectory()) {
                    //                    byte[] c = new byte[(int) jarEntry.getSize()];
                    //                    din.readFully(c);
                    byte[] c = readFully(input);
                    index.put(jarEntry.getName(), c);
                }
            }
        } catch (IOException iOException) {
        }
        System.out.println("Index done");
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream res = new ByteArrayOutputStream(2000);
        byte[] buff = new byte[2000];
        int read = 0;
        while ((read = in.read(buff)) != -1) {
            res.write(buff, 0, read);
        }
        return res.toByteArray();
    }

    public static JarInputStream getInput() throws IOException {
        return new JarInputStream(new ByteArrayInputStream(unpackedApp));
    }

    public static byte[] getEntry(String entry) {
        return index.get(entry);
    }

    public static class SpecialLoader extends ClassLoader {

        private final ProtectionDomain protectionDomain = getClass().getProtectionDomain();

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] entry = getEntry(name.replace('.', '/') + ".class");
            if (entry == null) {
                System.err.println("Class not found " + name);
                throw new ClassNotFoundException();
            }
            return defineClass(name, entry, 0, entry.length, protectionDomain);
        }

        private java.net.URL toURL(String name, final byte[] bytes) {
            try {
                return new java.net.URL("packedpool", "localhost", -1, name, new URLStreamHandler() {

                    @Override
                    protected URLConnection openConnection(URL url) throws IOException {
                        return new URLConnection(url) {

                            @Override
                            public void connect() throws IOException {
                            }

                            @Override
                            public Object getContent() throws IOException {
                                return bytes;
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(bytes);
                            }

                            @Override
                            public OutputStream getOutputStream() throws IOException {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public void setDoOutput(boolean dooutput) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public int getContentLength() {
                                return bytes.length;
                            }
                        };
                    }
                });
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Bad code", ex);
            }
        }

        @Override
        protected URL findResource(String name) {
            final byte[] entry = getEntry(name);
            if (entry != null) return toURL(name, entry);
            else {
                System.err.println("Resource not found " + name);
                return null;
            }
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            final byte[] entry = getEntry(name);
            if (entry != null) return Collections.enumeration(Arrays.asList(toURL(name, entry)));
            else {
                System.err.println("Resource not found " + name);
                return Collections.enumeration(Collections.<URL>emptyList());
            }
        }
    }
}
