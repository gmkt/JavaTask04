package ru.ifmo.rain.telnoj.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import javafx.util.Pair;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates implementation of input interface. Implements {@link JarImpler JarImpler interface}. Generates class with
 * same name as interface plus suffix <tt>Impl</tt>. Each {@link Implementor#printFunction(BufferedWriter, Executable, Class, Class, String)}
 * implemented method} returns default value {@link Implementor#getDefaultTypeValueString(Class) corresponding to it's type}.
 * Final variables are also set with default values of {@link Implementor#getDefaultTypeValueString(Class) corresponding type}.
 * <p>
 * Supports two formats of arguments passed to main: <code>/ %interface% /</code> - creates <tt>.java</tt> implementation of
 * <tt>%interface%</tt> and <code>/ -jar %interface% %jar-file% /</code> - creates <tt>.java</tt> implementation of
 * <tt>%interface%</tt>, compiles it and archives it in <tt>%jar-file%</tt>
 *
 * @version 0.1.0
 * @author Kirill Telnoy
 * @since 0.1.0
 * @see info.kgeorgiy.java.advanced.implementor.JarImpler
 */
public class Implementor implements JarImpler {
    /**
     * Message string printed because of incorrect format of input arguments passed to
     * {@link Implementor#main(String[]) main}
     *
     * @since 0.1.0
     */
    private static final String usageMessage =
            "Usage: java -jar %implementor% %class%\njava -jar %implementor% -jar %class% %jar-file%";

    /**
     * Space string used to indent code in generated <tt>.java</tt> files
     *
     * @since 0.1.0
     */
    private static final String spaceIndent = "    ";

    /**
     * Path which contains the directory where will be created the implementation of input interface. Will be initialised in
     * {@link #implement(Class, Path) implement} or {@link #implementJar(Class, Path) implementJar}.
     *
     * @since 0.1.0
     */
    private Path dirPath;

    /**
     * String name of implementation of input interface. Basically it is <code>%classSimpleName% + "Impl"</code>. Will be
     * initialised in {@link #implement(Class, Path) implement} or {@link #implementJar(Class, Path) implementJar}.
     *
     * @since 0.1.0
     */
    private String className;

    /**
     * Package of input interface. Will be initialised in {@link #implement(Class, Path) implement} or
     * {@link #implementJar(Class, Path) implementJar}.
     *
     * @since 0.1.0
     */
    private Package classPackage;

    /**
     * Enum containing types required to make general {@link #getModifiersString(int, modType) getModifiersString function}
     * appropriate for interfaces, fields, constructors and methods.
     *
     * @since 0.1.0
     */
    private enum modType {
        INTERFACE, FIELD, CONSTRUCTOR, METHOD, CLASS
    }

    /**
     * Constructor of Implementor. Initialises {@link #dirPath dirPath}, {@link #className className} and
     * {@link #classPackage} with default values. Accepts no parameters.
     *
     * @since 0.1.0
     */
    public Implementor() {
        dirPath = null;
        className = "";
        classPackage = null;
    }

    /**
     * Main function of {@link Implementor Implementor}. Supports two formats of passed arguments:
     * <code>/ %interface% /</code> - creates <tt>.java</tt> implementation of <tt>%interface%</tt> by using
     * {@link #implement(Class, Path) implement} and <code>/ -jar %interface% %jar-file% /</code> - creates
     * <tt>.java</tt> implementation of <tt>%interface%</tt>, compiles it and archives it in <tt>%jar-file%</tt>
     * by using {@link #implementJar(Class, Path) implementJar}.
     *
     * @param args String array supporting two formats
     * @see Implementor
     * @since 0.1.0
     * @see #implement(Class, Path)
     * @see #implementJar(Class, Path)
     */
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 3) System.out.println(usageMessage);
        else {
            Implementor imp = new Implementor();
            try {
                if (args[0].equals("-jar") && (args.length == 3)) {
                    imp.implementJar(Class.forName(args[1]), Paths.get(args[2]) );
                } else if (args.length == 1) imp.implement(Class.forName(args[0]), Paths.get(System.getProperty("user.dir")));
                else System.out.println(usageMessage);
            } catch (ClassNotFoundException e) {
                System.out.println("Invalid classname");
            } catch (ImplerException e) {
                System.out.println("ImplerException: " + e.getMessage());
            }
        }
    }

    /**
     * Creates <tt>.jar</tt> file (<tt>%jarFile%</tt>) containing the implementation of <tt>%token%</tt> interface. The
     * name of result class stored in <tt>.jar</tt> file is the same as <tt>%token%</tt> name plus suffix <tt>Impl</tt>.
     * Throws ImplerException in case of fail. Initialises {@link #classPackage classPackage}, {@link #dirPath dirPath}
     * and {@link #className className}
     *
     * @param token type token to create implementation for.
     * @param jarFile target <tt>.jar</tt> file.
     * @throws ImplerException when implementation cannot be generated.
     * @since 0.1.0
     * @see #implement(Class, Path)
     * @see #main(String[])
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Path filePath = Paths.get("");
        classPackage = token.getPackage();
        String packageName = "";
        int[] arr = new int[256];
        int j = 0;
        if (classPackage != null) {
            packageName = classPackage.getName().replace(".", File.separator) + File.separator;
            int i = packageName.indexOf(File.separator), m = 0;
            while (m < packageName.length()) {
                filePath = filePath.resolve(packageName.substring(m, i));
                m = i + 1;
                if (!Files.exists(filePath)) {
                    arr[j++] = m - 1;
                    try {
                        Files.createDirectory(filePath);
                    } catch (IOException e) {
                        throw new ImplerException("Cannot create directory");
                    }
                }
                i = packageName.indexOf(File.separator, m);
                if (i == -1) i = packageName.length();
            }
        }

        dirPath = filePath;
        implement(token, Paths.get(""));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        String fileName = className + ".java";
        filePath = filePath.resolve(fileName);
        compiler.run(null,null,null, filePath.toString());
        safeFileRemove(filePath);
        filePath = filePath.resolveSibling(fileName.replace(".java", ".class"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarFile.toFile()), manifest)) {
            addFileToJar(filePath.toFile(), outputStream);
        } catch (IOException e) {
            throw new ImplerException("Cannot create jar file");
        }
        safeFileRemove(filePath);

        for (int k = j - 1; k >= 0; k--) {
            safeFileRemove(Paths.get(packageName.substring(0, arr[k])));
            // TODO: (far future) find some use for filePath here
        }
        dirPath = null;
    }

    /**
     * Adds <tt>%source%</tt> file to <tt>%target%</tt> stream and all directories which required. Also fixes few
     * undocumented issues about creating <tt>.jar</tt>(or <tt>.zip</tt>) files.
     *
     * @param source file to put in target <tt>.jar</tt> file
     * @param target stream where to put <tt>%source%</tt>
     * @throws IOException when unable to write in target <tt>.jar</tt> file
     * @since 0.1.0
     * @see #implementJar(Class, Path)
     */
    private void addFileToJar(File source, JarOutputStream target) throws IOException {
        if (source == null) return;
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
            String name = source.getPath().replace("\\", "/");
            boolean isDir = source.isDirectory();
            if (!name.isEmpty()) {
                if (!name.endsWith("/") && isDir) name += "/";
                JarEntry entry = new JarEntry(name);
                entry.setTime(source.lastModified());
                target.putNextEntry(entry);
                if (!isDir) {
                    byte[] buffer = new byte[1024];
                    while (true) {
                        int count = in.read(buffer);
                        if (count == -1) break;
                        target.write(buffer, 0, count);
                    }
                }
                target.closeEntry();
            }
            if (isDir) {
                for (File nestedFile: Objects.requireNonNull(source.listFiles())) {
                    addFileToJar(nestedFile, target);
                }
            }
        }
    }

    /**
     * Remove <tt>%file%</tt> ignoring IOException. Because it is not mandatory to remove temporary files.
     *
     * @param file file to delete
     * @since 0.1.0
     * @see #implementJar(Class, Path)
     */
    private void safeFileRemove(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            //throw new ImplerException("Cannot remove temp file");
        }
    }

    /**
     * Creates <tt>.java</tt> file containing the implementation of <tt>%token%</tt> interface. The name of result class
     * is the same as <tt>%token%</tt> name plus <tt>Impl</tt> suffix. Throws ImplerException in case of fail.
     * Initialises {@link #classPackage classPackage}, {@link #dirPath dirPath} and {@link #className className}
     *
     * @param token type token to create implementation for.
     * @param root path where to create implementation of <tt>%token%</tt>
     * @throws ImplerException when implementation cannot be generated.
     * @since 0.1.0
     * @see #implementJar(Class, Path)
     * @see #main(String[])
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (token.isLocalClass() || token.isAnonymousClass() || token.isMemberClass() ||
                token.isPrimitive() || Modifier.isFinal(token.getModifiers()) || token.equals(Enum.class)
                || (token.getDeclaredConstructors().length == 0 && !token.isInterface())) {
            throw new ImplerException("Not supported");
        }
        String packageName = "";
        if (classPackage == null) classPackage = token.getPackage();
        if (classPackage != null) packageName = classPackage.getName();
        if (dirPath == null) {
            dirPath = root;
            if (classPackage != null) {
                dirPath = dirPath.resolve(packageName.replace(".", File.separator) + File.separator);
            }
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                throw new ImplerException("Unable to create directory");
            }
        }
        //System.out.println(dirPath.toString() + File.separator + token.getSimpleName());
        className = token.getSimpleName() + "Impl";
        try (BufferedWriter writer = Files.newBufferedWriter(dirPath.resolve(className + ".java"),
                StandardCharsets.UTF_8)) {
            if (classPackage != null) writer.write("package " + packageName + ";\n\n");
            //writer.write("import java.lang.annotation.*;\n" + getAnnotationsString(token.getAnnotations(), "\n"));
            printClass(writer, token, className);
        } catch (IOException e) {
            throw new ImplerException("Unable to create java file");
        }
        classPackage = null;
    }

    /**
     * Class made to implement true version of {@link Method#equals(Object) Method.equals()} corresponding to
     * Java syntax
     *
     * @since 0.1.0
     * @see #printClass(BufferedWriter, Class, String)
     * @see MethodIdentity#equals(Object)
     * @see MethodIdentity#hashCode()
     */
    private class MethodIdentity {
        /**
         * Pair containing method name and parameters count. Used to get {@link MethodIdentity#hashCode() hashcode}
         *
         * @since 0.1.0
         */
        private Pair<String, Integer> methodHashObject;

        /**
         * Array containing types of all parameters which should be passed to current method.
         *
         * @since 0.1.0
         */
        private Class<?> parameterTypes[];

        /**
         * Constructor sets {@link MethodIdentity#methodHashObject methodHashObject} and
         * {@link MethodIdentity#parameterTypes} to values got from method passed as parameter
         *
         * @param method current method which information is stored
         */
        MethodIdentity(Method method) {
            methodHashObject = new Pair<>(method.getName(), method.getParameterCount());
            parameterTypes = method.getParameterTypes();
        }

        /**
         * True version of {@link Method#equals(Object) Method.equals()} made corresponding to Java syntax
         *
         * @param obj method to check equality with
         * @return true if methods are counted as equal by Java compiler, false otherwise
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodIdentity) {
                MethodIdentity methodIdentity = (MethodIdentity) obj;
                if (methodIdentity.methodHashObject.getKey().equals(methodHashObject.getKey()) &&
                        methodIdentity.methodHashObject.getValue().equals(methodHashObject.getValue())) {
                    boolean f = true;
                    for (int i = 0; i < methodHashObject.getValue(); i++) {
                        if (!parameterTypes[i].isAssignableFrom(methodIdentity.parameterTypes[i])) {
                            f = false;
                            break;
                        }
                    }
                    if (f) return true;
                    for (int i = 0; i < methodHashObject.getValue(); i++) {
                        if (!parameterTypes[i].isInstance(methodIdentity.parameterTypes[i]))
                            return false;
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Hashcode function returning {@link MethodIdentity#methodHashObject methodHashObject's} hash
         *
         * @return method's hashcode
         */
        @Override
        public int hashCode() {
            return methodHashObject.hashCode();
        }
    }

    /**
     * Prints by using <tt>writer</tt> the code of class extending(implementing) <tt>token</tt> with name
     * passed as <tt>localClassName</tt>
     *
     * @param writer BufferedWriter which used to write result implementation to
     * @param token class to generate implementation for
     * @param localClassName string containing the name of class to generate code for
     * @throws IOException in case of inability to write using writer
     * @throws ImplerException when implementation cannot be generated.
     */
    private void printClass(BufferedWriter writer, Class<?> token, String localClassName) throws IOException, ImplerException {
        modType tokenType = modType.CLASS;
        String tokenString = " extends ";
        if (token.isInterface()) {
            tokenType = modType.INTERFACE;
            tokenString = " implements ";
        }
        writer.write(getModifiersString(token.getModifiers(), tokenType) + "class ");
        writer.write(escapeNonAscii(localClassName + tokenString + token.getSimpleName()));
        writer.write(" {\n");

        Field[] fields = token.getFields();
        for (int i = 0; i < fields.length; i++) {
            writer.write(spaceIndent + getModifiersString(fields[i].getModifiers(), modType.FIELD) +
                    getAnnotatedTypeString(fields[i].getType(), token) + " a" + i);
            if (Modifier.isFinal(fields[i].getModifiers())) {
                writer.write(" = " + getDefaultTypeValueString(fields[i].getType()));
            }
            writer.write(";\n");
        }
        writer.newLine();

        Constructor<?> constructors[] = token.getConstructors();
        for (Constructor constructor : constructors) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                printFunction(writer, constructor, null, token, localClassName);
            }
        }
        if (constructors.length == 0 && !token.isInterface()) {
            boolean f = false;
            for (Constructor constructor : token.getDeclaredConstructors()) {
                if (!Modifier.isPrivate(constructor.getModifiers())) {
                    printFunction(writer, constructor, null, token, localClassName);
                    f = true;
                }
            }
            if (!f) throw new ImplerException("Cannot extend class with private constructors");
        }

        Iterator<Method> iterator = Stream.concat(
                Arrays.stream(token.getDeclaredMethods()),
                Arrays.stream(token.getMethods())).collect(Collectors.toMap(
                        MethodIdentity::new, Function.identity(), (a, b) -> a))
                .entrySet().stream().map(Map.Entry::getValue).distinct().iterator();
        while (iterator.hasNext()) {
            Method method = iterator.next();
            if (!Modifier.isFinal(method.getModifiers()) && !Modifier.isNative(method.getModifiers())) {
                printFunction(writer, method, method.getReturnType(), token, method.getName());
            }
        }

        for (Class<?> innerClass : token.getClasses()) {
            if (!Modifier.isFinal(innerClass.getModifiers()) && !Modifier.isPrivate(innerClass.getModifiers())) {
                printClass(writer, innerClass, getImplName(innerClass));
            }
        }

        writer.write("\n}");
    }

    /**
     * Returns String containing name of extending(implementing) class
     *
     * @param token class(interface) to extend(implement)
     * @return String containing name of extending(implementing) class(interface)
     */
    private String getImplName(Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    /**
     * Returns String with few issues with non-ASCII symbols fixed.
     *
     * @param str String which might contain non-ASCII symbols
     * @return fixed String
     * @since 0.1.0
     * @see #implement(Class, Path)
     */
    private String escapeNonAscii(String str) {
        StringBuilder retStr = new StringBuilder();
        for(int i = 0; i < str.length(); i++) {
            int cp = Character.codePointAt(str, i);
            int charCount = Character.charCount(cp);
            if (charCount > 1) {
                i += charCount - 1;
                if (i >= str.length()) {
                    throw new IllegalArgumentException("truncated unexpectedly");
                }
            }
            if (cp < 128) {
                retStr.appendCodePoint(cp);
            } else {
                retStr.append(String.format("\\u%04x", cp));
            }
        }
        return retStr.toString();
    }

    /**
     * Prints implementation of method or constructor (<tt>%func</tt>) in <tt>%writer%</tt>. (If <tt>%returnType%</tt>
     * is not null then it prints function implementation, constructor implementation otherwise.)
     *
     * @param writer BufferedWriter which used to write result implementation to
     * @param func method or constructor which implementation is generated
     * @param returnType return type of <tt>%func%</tt> if it is method, otherwise <tt>null</tt>
     * @param token class to generate implementation for
     * @param funcName function string name
     * @throws IOException when unable to write to <tt>%writer%</tt>
     * @since 0.1.0
     * @see #implement(Class, Path)
     */
    private void printFunction(BufferedWriter writer, Executable func, Class<?> returnType, Class<?> token, String funcName) throws IOException {
        writer.write(spaceIndent);
        if (returnType == null) writer.write(getModifiersString(func.getModifiers(), modType.CONSTRUCTOR));
        else writer.write(getModifiersString(func.getModifiers(), modType.METHOD) +
                getAnnotatedTypeString(returnType, token) + " ");
        writer.write(funcName);
        Class<?> parameterTypes[] = func.getParameterTypes();
        writer.write("(" + getTypesStringWithDelimiter(parameterTypes, token, true) + ") ");
        writer.write(getTypesStringWithDelimiter(func.getExceptionTypes(), token, false) + "{ \n");
        if (returnType != null) {
            writer.write(spaceIndent + spaceIndent + "return");
            writer.write(getDefaultTypeValueString(returnType));
        } else {
            writer.write(spaceIndent + spaceIndent + "super(");
            for (int i = 0; i < parameterTypes.length - 1; i++) {
                writer.write("variable" + i + ", ");
            }
            if (parameterTypes.length > 0) {
                writer.write("variable" + (parameterTypes.length - 1));
            }
            writer.write(")");
        }
        writer.write(";\n" + spaceIndent + "}\n\n");
    }

    /**
     * Returns String containing list of exceptions (with <code>throws</code> in beginning) or parameters in function.
     * String is created iteratively with {@link StringBuilder StringBuilder}. Variable names are generated automatically
     * by adding number to <tt>variable</tt>.
     *
     * @param classes classes which names with certain delimiters will form result String
     * @param token class to generate implementation for
     * @param variable determines whether String should be generated for exceptions(<tt>false</tt>) or parameters (<tt>true</tt>)
     * @return result String containing list of exceptions or parameters.
     * @since 0.1.0
     * @see #printFunction(BufferedWriter, Executable, Class, Class, String)
     */
    private String getTypesStringWithDelimiter(Class<?>[] classes, Class<?> token, boolean variable) {
        if (classes.length == 0) return "";
        StringBuilder s = new StringBuilder();
        if (!variable) s.append("throws ");
        for (int i = 0; i < classes.length - 1; i++) {
            s.append(escapeNonAscii(getAnnotatedTypeString(classes[i], token)));
            if (variable) s.append(" variable").append(i);
            s.append(", ");
        }
        s.append(escapeNonAscii(getAnnotatedTypeString(classes[classes.length - 1], token))).append(" ");
        if (variable) s.append("variable").append(classes.length - 1);
        return s.toString();
    }

    /**
     * Returns modifiers String according to <tt>%type%</tt> of target.
     * Uses {@link java.lang.reflect.Modifier#toString(int) Modifier.toString(int)}
     *
     * @param modifiers int value of target's modifiers
     * @param type target's type
     * @return String containing correct modifiers
     * @since 0.1.0
     * @see modType
     * @see #implement(Class, Path)
     * @see #printFunction(BufferedWriter, Executable, Class, Class, String)
     */
    private String getModifiersString(int modifiers, modType type) {
        modifiers &= ~Modifier.ABSTRACT & ~Modifier.INTERFACE;
        switch (type) {
            case INTERFACE: {
                modifiers &= Modifier.interfaceModifiers();
                break;
            }
            case FIELD: {
                modifiers &= Modifier.fieldModifiers();
                break;
            }
            case CONSTRUCTOR: {
                modifiers &= Modifier.constructorModifiers();
                break;
            }
            case METHOD: {
                modifiers &= Modifier.methodModifiers();
                break;
            }
            case CLASS: {
                modifiers &= Modifier.classModifiers();
                break;
            }
        }
        String s = Modifier.toString(modifiers);
        if (s.length() > 0) s += " ";
        return s;
    }

    /**
     * Constructs String with default value for <tt>%type%</tt>.
     *
     * @param type input type
     * @return String with default value
     * @since 0.1.0
     * @see #implement(Class, Path)
     * @see "<a href = 'https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html'>Primitive Data Types</a>"
     */
    private String getDefaultTypeValueString(Class<?> type) {
        if (type.isPrimitive()) {
            if (type.equals(Void.TYPE)) return "";
            if (type.equals(Boolean.TYPE)) return " false";
            if (type.equals(Character.TYPE)) return " '\\u0000'";
            if (type.equals(Long.TYPE)) return " 0L";
            if (type.equals(Integer.TYPE) || type.equals(Byte.TYPE) || type.equals(Short.TYPE)) return " 0";
            if (type.equals(Float.TYPE)) return " 0.0f";
            if (type.equals(Double.TYPE)) return " 0.0d";
        }
        return " null";
    }

    /**
     * Constructs String with all Annotation from <tt>%annotations%</tt> array delimited by <tt>%delimiter%</tt>.
     * Currently FunctionalInterface annotation is not supported.
     *
     * @param annotations input annotations array
     * @param delimiter delimiter which is put between them
     * @return String containing annotations from <tt>%annotations%</tt>
     * @since 0.1.0
     * @see #getAnnotatedTypeString(Class, Class)
     * @see #implement(Class, Path)
     */
    private String getAnnotationsString(Annotation[] annotations, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (Annotation annotation : annotations) {
            if (!annotation.annotationType().equals(FunctionalInterface.class))
                result.append(annotation.toString()).append(delimiter);
        }
        return result.toString();
    }

    /**
     * Generates String containing type annotations and name. Uses {@link #getAnnotationsString(Annotation[], String) getAnnotationsString}
     * to get annotations String
     *
     * @param type input type which to create String for
     * @param tokenType class to generate implementation for
     * @return String containing type annotations plus <tt>" "</tt> plus type name
     * @since 0.1.0
     * @see #printFunction(BufferedWriter, Executable, Class, Class, String)
     * @see #getTypesStringWithDelimiter(Class[], Class, boolean)
     * @see #implement(Class, Path)
     */
    private String getAnnotatedTypeString(Class<?> type, Class<?> tokenType) {
        String s = getAnnotationsString(type.getDeclaredAnnotations(), " ");
        if (tokenType.getPackage().equals(type.getPackage())) s += type.getSimpleName();
        else s += type.getCanonicalName();
        return s;
    }
}
