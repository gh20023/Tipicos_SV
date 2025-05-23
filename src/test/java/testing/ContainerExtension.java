/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package testing;

import java.nio.file.Paths;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 *
 * @author morales
 */
public class ContainerExtension implements BeforeAllCallback, AfterAllCallback{
    
    private static final Object lock = new Object();
    private static boolean postgresStart = false;
    private static boolean libertyStart = false;
    private static int numClassTest = 0;
    private static boolean SystemTest = false;
    
    protected static final Network red = Network.newNetwork();
    
    protected static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("PupaSV")
            .withPassword("abc123")
            .withUsername("postgres")
            .withExposedPorts(5432)
            .withNetwork(red)
            .withNetworkAliases("db");
    
    protected static final GenericContainer<?> openliberty = new GenericContainer<>("openliberty:latest")
            .withExposedPorts(9080)
            .withCopyFileToContainer(getWarFile(), "/home/usuario/wlp/usr/servers/app/dropins/PupaSV-1.0-SNAPSHOT.war")
            .withNetwork(red)
            .withEnv("PGPASSWORD", "abc123")
            .withEnv("PGUSER", "postgres")
            .withEnv("PGDBNAME", "PupaSV")
            .withEnv("PGPORT", "5432")
            .withEnv("PGHOST", "db")
            .dependsOn(postgres)
            .waitingFor(Wait.forLogMessage(".*The app server is ready to run a smarter planet.*", 1));
    
    
    public static void configurarParaE2E(){
        SystemTest = true;
        postgres = postgres.withInitScript("pupadb_E2E.sql");
    }
    
    public static void configurarParaIT(){
        SystemTest= false;
        postgres = postgres.withInitScript("pupa_db.sql");
    }
    
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception{
        synchronized (lock) {
            //Configurar segun el tipo de prueba
            
             if (context.getTestClass().isPresent()) {
                Class<?> testClass = context.getTestClass().get();
                if (testClass.getName().contains("E2E") || testClass.getName().contains("SystemTest")) {
                    configurarParaE2E();
                } else {
                    configurarParaIT();
                }
            }
             
            //determinar si se necesita el contenedor de liberty
            boolean needLiberty = context.getTestClass()
                    .map(cls -> cls.isAnnotationPresent(NeedsLiberty.class))
                    .orElse(false);
            
         //   System.out.println("CLASE ANOTACION OPENLIBERTY ESTADO"+ needLiberty);
            
            numClassTest++;
            
            if(!postgresStart){
                postgres.start();
                postgresStart = true;
            }
            
            if(needLiberty && !libertyStart){
                openliberty.start();
                libertyStart = true;
            }
            
            //configurar shutdown hook solo una vez
            if(numClassTest == 1){
                Runtime.getRuntime().addShutdownHook(new Thread(()->{
                    synchronized (lock) {
                        if(libertyStart){
                            openliberty.stop();
                            libertyStart = false;
                        }
                        if(postgresStart){
                            postgres.stop();
                            postgresStart=false;
                        }
                    }
                }));
            }
            
            
        }
    }
    
     @Override
    public void afterAll(ExtensionContext context) throws Exception {
        synchronized (ContainerExtension.class) {
            numClassTest--;
//            System.out.println("LOGS OPENLIBERTY");
//            System.out.println("\n "+ openliberty.getLogs());
//            if (numClassTest == 0) {
//                if (libertyStart) {
//                    openliberty.stop();
//                    libertyStart = false;
//                }
//                if (postgresStart) {
//                    postgres.stop();
//                    postgresStart = false;
//                }
//            }    
        }
    }
    
      // Métodos para acceder a los contenedores
    public static PostgreSQLContainer<?> getPostgres() {
        return postgres;
    }
    
    public static GenericContainer<?> getOpenLiberty() {
        return openliberty;
    }
    
    private static MountableFile getWarFile() {
        return MountableFile.forHostPath(Paths.get("target/PupaSv-1.0-SNAPSHOT.war").toAbsolutePath());
    }
    
     public static boolean isSystemTest() {
        return SystemTest;
    }
}
