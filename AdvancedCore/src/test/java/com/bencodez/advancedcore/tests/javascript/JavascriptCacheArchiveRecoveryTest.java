package com.bencodez.advancedcore.tests.javascript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;import java.net.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import java.util.jar.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;
class JavascriptCacheArchiveRecoveryTest {
 @TempDir Path directory;
 private static byte[] jar(String text)throws IOException{var b=new ByteArrayOutputStream();try(var z=new JarOutputStream(b)){z.putNextEntry(new JarEntry("entry.txt"));z.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();}return b.toByteArray();}
 @Test void repairsCorruptNashorn()throws Exception{repair("nashorn-core",new byte[]{1,2,3},false);}
 @Test void repairsEmptyNashorn()throws Exception{repair("nashorn-core",new byte[0],false);}
 @Test void repairsTruncatedNashorn()throws Exception{repair("nashorn-core",Arrays.copyOf(jar("valid"),20),false);}
 @Test void repairsCorruptAsm()throws Exception{repair("asm",new byte[]{1,2,3},false);}
 @Test void repairsEmptyAsm()throws Exception{repair("asm",new byte[0],false);}
 @Test void repairsTruncatedAsm()throws Exception{repair("asm",Arrays.copyOf(jar("valid"),20),false);}
 @Test void repairsAllStructurallyBrokenArchives()throws Exception{repair("nashorn-core",new byte[]{1},true);}
 private void repair(String artifact,byte[] broken,boolean all)throws Exception{
  byte[] good=jar("replacement");var f=new Fixture(good);Path target=f.folder.resolve(file(artifact));Files.write(target,broken);if(all)for(String a:ARTIFACTS)Files.write(f.folder.resolve(file(a)),broken);
  try(var ignored=f.peer()){assertEquals(6,load(f.handler).size());}
  assertArrayEquals(good,Files.readAllBytes(target));assertEquals(all?6:1,f.requests.get());f.preserved();
 }
 @Test void disabledDownloadLeavesBrokenBytesAndReturnsUnavailable()throws Exception{
  var f=new Fixture(jar("unused"));f.handler.setAutoDownload(false);Path target=f.folder.resolve(file("asm"));byte[] broken={7};Files.write(target,broken);
  try(var ignored=f.peer()){assertTrue(load(f.handler).isEmpty());}assertArrayEquals(broken,Files.readAllBytes(target));assertEquals(0,f.requests.get());f.preserved();
 }
 @Test void validCustomArchivesArePreservedWithoutCanonicalBytePolicy()throws Exception{
  var f=new Fixture(jar("different"));f.handler.setAutoDownload(false);byte[] before=Files.readAllBytes(f.folder.resolve(file("nashorn-core")));
  try(var ignored=f.peer()){assertEquals(6,load(f.handler).size());}assertArrayEquals(before,Files.readAllBytes(f.folder.resolve(file("nashorn-core"))));assertEquals(0,f.requests.get());f.preserved();
 }
 @Test void invalidReplacementCannotOverwriteAConcurrentGoodPublication()throws Exception{
  var f=new Fixture(new byte[]{1,2,3});Path target=f.folder.resolve(file("asm"));Files.write(target,new byte[]{9});byte[] good=jar("other attempt");f.beforeBody=()->{try{Files.write(target,good);}catch(IOException e){throw new UncheckedIOException(e);}};
  try(var ignored=f.peer()){assertThrows(IOException.class,()->load(f.handler));}assertArrayEquals(good,Files.readAllBytes(target));f.preserved();
 }
 @Test void healthyArchivePublishedDuringRepairIsPreserved()throws Exception{
  var f=new Fixture(jar("downloaded"));Path target=f.folder.resolve(file("asm"));Files.write(target,new byte[]{9});byte[] good=jar("operator publication");f.beforeBody=()->{try{Files.write(target,good);}catch(IOException e){throw new UncheckedIOException(e);}};
  try(var ignored=f.peer()){assertEquals(6,load(f.handler).size());}assertArrayEquals(good,Files.readAllBytes(target));assertEquals(1,f.requests.get());f.preserved();
 }
 @Test void unsupportedAtomicPublicationPreservesBrokenCache()throws Exception{
  var f=new Fixture(jar("downloaded"));Path target=f.folder.resolve(file("asm"));byte[] broken={9};Files.write(target,broken);
  try(var ignored=f.peer();var files=mockStatic(Files.class,CALLS_REAL_METHODS)){
   files.when(()->Files.move(any(Path.class),eq(target),eq(StandardCopyOption.ATOMIC_MOVE),eq(StandardCopyOption.REPLACE_EXISTING))).thenThrow(new AtomicMoveNotSupportedException("candidate","cache","unsupported"));
   assertThrows(AtomicMoveNotSupportedException.class,()->load(f.handler));
  }assertArrayEquals(broken,Files.readAllBytes(target));f.preserved();
 }
 @Test void directoryAtManagedPathIsNeverRemoved()throws Exception{
  var f=new Fixture(jar("unused"));Path target=f.folder.resolve(file("asm"));Files.delete(target);Files.createDirectory(target);Files.writeString(target.resolve("operator.txt"),"preserve");
  try(var ignored=f.peer()){assertThrows(IOException.class,()->load(f.handler));}assertEquals("preserve",Files.readString(target.resolve("operator.txt")));assertEquals(0,f.requests.get());f.preserved();
 }
 private static final List<String> ARTIFACTS=List.of("nashorn-core","asm","asm-commons","asm-analysis","asm-tree","asm-util");
 private static String file(String a){return a+"-"+(a.equals("nashorn-core")?"15.7":"9.8")+".jar";}
 @SuppressWarnings("unchecked") private static List<Path> load(JavascriptEngineHandler h)throws Exception{
  var m=JavascriptEngineHandler.class.getDeclaredMethod("getOrDownloadJars",String.class,String.class);m.setAccessible(true);try{return (List<Path>)m.invoke(h,"15.7","9.8");}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}
 }
 private class Fixture{
  final Path folder=directory.resolve("libs/nashorn/15.7");final JavascriptEngineHandler handler=new JavascriptEngineHandler();final AtomicInteger requests=new AtomicInteger();final byte[] payload;Runnable beforeBody=()->{};
  Fixture(byte[] payload)throws Exception{this.payload=payload;Files.createDirectories(folder);for(String a:ARTIFACTS)Files.write(folder.resolve(file(a)),jar("custom "+a));Files.writeString(folder.resolve("existing.jar.tmp"),"preserve");Files.writeString(folder.resolve("operator.jar"),"unrelated");var plugin=mock(AdvancedCorePlugin.class);when(plugin.getDataFolder()).thenReturn(directory.toFile());when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());handler.setPlugin(plugin);handler.setAutoDownload(true);}
  org.mockito.MockedStatic<URI> peer()throws Exception{
   Map<String,URI> peers=new HashMap<>();for(String a:ARTIFACTS){String u="https://repo1.maven.org/maven2/"+(a.equals("nashorn-core")?"org/openjdk/nashorn/":"org/ow2/asm/")+a+"/"+(a.equals("nashorn-core")?"15.7":"9.8")+"/"+file(a);URL url=URL.of(URI.create(u),new URLStreamHandler(){protected URLConnection openConnection(URL value){requests.incrementAndGet();return new HttpURLConnection(value){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public InputStream getInputStream(){beforeBody.run();return new ByteArrayInputStream(payload);}};}});URI uri=mock(URI.class);when(uri.toURL()).thenReturn(url);peers.put(u,uri);}
   var statics=mockStatic(URI.class,CALLS_REAL_METHODS);statics.when(()->URI.create(anyString())).thenAnswer(call->{String u=call.getArgument(0);return peers.containsKey(u)?peers.get(u):call.callRealMethod();});return statics;
  }
  void preserved()throws Exception{assertEquals("preserve",Files.readString(folder.resolve("existing.jar.tmp")));assertEquals("unrelated",Files.readString(folder.resolve("operator.jar")));try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith("download-")));}}
 }
}
