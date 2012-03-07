package net.r0kit.brijj.demo;

import net.r0kit.brijj.RemoteRequestProxy;
import net.r0kit.brijj.demo.people.People;
import net.r0kit.brijj.demo.people.Person;
import net.r0kit.brijj.demo.simple.Demo;
import net.r0kit.brijj.demo.simple.Intro;
import net.r0kit.brijj.demo.simple.UploadDownload;
import net.r0kit.brijj.servlet.BrijjServlet;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;



public class BrijjDemo {  

  public static void main(String[] argsx) throws Exception {
    Server server = new Server(Integer.parseInt(argsx[0]));

//    MapStoreProvider<Person> provider = new MapStoreProvider<Person>(People.createCrowd(1000), Person.class);
//    Data.register("largeCrowd", provider);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
 
 //   context.addServlet(Favicon.class, "/favicon.ico");

    // RandomData.class
    // Person.class
    // Message.class
    Class<?>[] ccc = new Class<?>[] {
        Intro.class, Demo.class, UploadDownload.class, People.class,
          Person.class
          };
    
    for(Class<?> c : ccc) RemoteRequestProxy.register(c);

      context.addServlet(BrijjServlet.class,"/brijj/*");

      context.addServlet(DefaultServlet.class, "/*");
      context.setResourceBase(BrijjDemo.class.getResource("/net/r0kit/brijj/demo").toExternalForm());
      
      
 /*     context.addServlet(IndexHandler.class,"/index.html");
      context.addServlet(TestHandler.class,"/test/*");
      context.addServlet(GeneratedJavaScriptHandler.class,"/interface/*");
      context.addServlet(DownloadHandler.class,DownloadHandler.downloadHandlerUrl+"/*");
      context.addServlet(CallHandler.class,"/call/plaincall/*");
      context.addServlet(EngineHandler.class,"/engine.js");
   */   
    
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    contexts.setHandlers(new Handler[] { context } );

    server.setHandler(contexts);
    server.start();
    server.join();
  }
}
