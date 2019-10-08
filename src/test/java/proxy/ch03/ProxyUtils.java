package proxy.ch03;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class ProxyUtils {

  public static Object newProxyInstance(Object target) throws Exception{

    String content = "";

    String packageContent = "package proxy;";

    Class targetInfo = target.getClass().getInterfaces()[0];

    String targetInfoName = targetInfo.getSimpleName();

    String importContent = "import "+targetInfo.getName()+";";

    String classContent = "public class $Proxy implements "+targetInfoName+"{";

    /**/
    String fieldContent = "private "+targetInfoName+" targetfield;";

    String constructContent = "public $Proxy("+targetInfoName+" targetfield){" +
      "this.targetfield = targetfield; }";

    String methodsContent = "";
    Method[] methods = target.getClass().getDeclaredMethods();

    for (Method method : methods) {
      String methodName = method.getName();
      Class returnType = method.getReturnType();

      Class<?>[] parameterTypes = method.getParameterTypes();

      String agrsContent = "";

      String agrsName = "";

      int i=0;

      for (Class<?> parameterType : parameterTypes) {
        String simpleName = parameterType.getSimpleName();
        agrsContent += simpleName+" p"+i+",";
        agrsName += " p"+i+",";
      }
      if (agrsContent.length() > 0){
        agrsContent = agrsContent.substring(0,agrsContent.length()-1);
        agrsName = agrsName.substring(0,agrsName.length()-1);
      }

      methodsContent = "public "+returnType+" "+methodName+"("+agrsContent+"){"
        +"System.out.println(\"log-------------------start\");"+"targetfield."+methodName
      +"("+agrsName+");}";

    }

    content += packageContent + importContent + classContent
      +fieldContent + constructContent + methodsContent+";}";

    File file = new File("/Users/yinke/work/mybatis-3/src/test/java/proxy/$Proxy.java");
    if (!file.exists()) {
      file.createNewFile();
    }

    FileWriter fileWriter = new FileWriter(file);
    fileWriter.write(content);
    fileWriter.flush();
    fileWriter.close();


    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager standardJavaFileManager = javaCompiler.getStandardFileManager(null,null,null);
    Iterable iterable = standardJavaFileManager.getJavaFileObjects(file);

    JavaCompiler.CompilationTask task = javaCompiler.getTask(null,standardJavaFileManager,null,null,null,iterable);
    task.call();
    standardJavaFileManager.close();

    /*//使用自定义类加载器
    MyClassLoader loader = new MyClassLoader("/Users/yinke/work/mybatis-3/src/test/java/proxy/");
    //得到动态代理类的反射对象
    Class<?> aClass = loader.findClass("$Proxy");*/

    URL[]  urls = new URL[]{new URL("/Users/yinke/work/mybatis-3/src/test/java/proxy")};
    URLClassLoader urlClassLoader = new URLClassLoader(urls);
    Class aClass = urlClassLoader.loadClass("$Proxy");
    Constructor constructor = aClass.getConstructor(targetInfo);
    Object o = constructor.newInstance(target);
    return o;
  }
}
