package com.bencodez.advancedcore.tests.javascript;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;

class JavascriptDependencyDownloadTest {

    private static final byte[] VALID = {1, 2, 3};
    private static final byte[] REPLACEMENT = {4, 5, 6};

    @TempDir
    Path directory;

    @Test
    void interruptedTransferRemovesPartialCandidateAndPreservesExistingFiles() throws Exception {
        Path target = Files.write(directory.resolve("dependency.jar"), VALID);
        Path sibling = Files.write(directory.resolve("other.jar.tmp"), VALID);
        InterruptedIOException interrupted = new InterruptedIOException("transfer interrupted");
        TestConnection connection = new TestConnection(new ByteArrayInputStream(REPLACEMENT) {
            @Override
            public long transferTo(OutputStream output) throws IOException {
                output.write(REPLACEMENT[0]);
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        });

        DownloadInvocation download = prepareDownload(connection);
        try {
            assertSame(interrupted, assertThrows(InterruptedIOException.class, () -> download.run(target)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(connection.closed);
        assertTrue(connection.disconnected);
        assertArrayEquals(VALID, Files.readAllBytes(target));
        assertArrayEquals(VALID, Files.readAllBytes(sibling));
        assertEquals(Set.of(target, sibling), files());

        prepareDownload(new TestConnection(new ByteArrayInputStream(REPLACEMENT))).run(target);
        assertArrayEquals(REPLACEMENT, Files.readAllBytes(target));
        assertEquals(Set.of(target, sibling), files());
    }

    @Test
    void cancelledTransferWithNormalEofDoesNotPublish() throws Exception {
        Path target = Files.write(directory.resolve("dependency.jar"), VALID);
        TestConnection connection = new TestConnection(new ByteArrayInputStream(REPLACEMENT) {
            @Override
            public long transferTo(OutputStream output) throws IOException {
                long bytes = super.transferTo(output);
                Thread.currentThread().interrupt();
                return bytes;
            }
        });
        DownloadInvocation download = prepareDownload(connection);
        try {
            assertThrows(InterruptedIOException.class, () -> download.run(target));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(connection.closed);
        assertTrue(connection.disconnected);
        assertArrayEquals(VALID, Files.readAllBytes(target));
        assertEquals(Set.of(target), files());
    }

    @Test
    void interruptedAttemptDoesNotRemoveAnotherAttemptsPublication() throws Exception {
        Path target = directory.resolve("dependency.jar");
        Path sibling = Files.write(directory.resolve("dependency.jar.tmp"), VALID);
        DownloadInvocation competing = prepareDownload(new TestConnection(new ByteArrayInputStream(REPLACEMENT)));
        IOException transferFailure = new IOException("transfer failed");
        TestConnection connection = new TestConnection(new ByteArrayInputStream(VALID) {
            @Override
            public long transferTo(OutputStream output) throws IOException {
                output.write(VALID[0]);
                Set<Path> inFlight = files();
                assertEquals(2, inFlight.size());
                var thread = Executors.newSingleThreadExecutor();
                try {
                    thread.submit(() -> {
                        competing.run(target);
                        return null;
                    }).get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("Competing download did not complete", e);
                } finally {
                    thread.shutdownNow();
                }
                assertTrue(files().containsAll(inFlight));
                throw transferFailure;
            }
        });

        DownloadInvocation download = prepareDownload(connection);
        assertSame(transferFailure, assertThrows(IOException.class, () -> download.run(target)));
        assertTrue(connection.closed);
        assertTrue(connection.disconnected);
        assertArrayEquals(REPLACEMENT, Files.readAllBytes(target));
        assertArrayEquals(VALID, Files.readAllBytes(sibling));
        assertEquals(Set.of(target, sibling), files());
    }

    @Test
    void outputOpenFailureRemovesCandidateAndClosesInput() throws Exception {
        assertOutputFailureCleanup(true);
    }

    @Test
    void outputWriteFailureRemovesCandidateAndClosesInput() throws Exception {
        assertOutputFailureCleanup(false);
    }

    private void assertOutputFailureCleanup(boolean failOnOpen) throws Exception {
        Path target = Files.write(directory.resolve("dependency.jar"), VALID);
        IOException outputFailure = new IOException("output failed");
        AtomicBoolean outputClosed = new AtomicBoolean();
        TestConnection connection = new TestConnection(new ByteArrayInputStream(REPLACEMENT));
        DownloadInvocation download = prepareDownload(connection);
        try (var ignored = mockStatic(Files.class, invocation -> {
            if (!invocation.getMethod().getName().equals("newOutputStream")) {
                return invocation.callRealMethod();
            }
            if (failOnOpen) {
                throw outputFailure;
            }
            return new FilterOutputStream((OutputStream) invocation.callRealMethod()) {
                @Override
                public void write(int value) throws IOException {
                    out.write(value);
                    throw outputFailure;
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    outputClosed.set(true);
                }
            };
        })) {
            assertSame(outputFailure, assertThrows(IOException.class, () -> download.run(target)));
        }
        assertEquals(!failOnOpen, outputClosed.get());
        assertTrue(connection.closed);
        assertTrue(connection.disconnected);
        assertArrayEquals(VALID, Files.readAllBytes(target));
        assertEquals(Set.of(target), files());
    }

    @Test
    void emptyBodyAndFailedMoveRemoveCandidates() throws Exception {
        Path target = Files.createDirectory(directory.resolve("dependency.jar"));
        Path child = Files.write(target.resolve("keep"), VALID);
        TestConnection empty = new TestConnection(new ByteArrayInputStream(new byte[0]));
        DownloadInvocation emptyDownload = prepareDownload(empty);
        IOException emptyFailure = assertThrows(IOException.class, () -> emptyDownload.run(target));
        assertTrue(emptyFailure.getMessage().startsWith("Downloaded empty jar"));
        assertEquals(Set.of(target), files());

        TestConnection nonempty = new TestConnection(new ByteArrayInputStream(REPLACEMENT));
        DownloadInvocation nonemptyDownload = prepareDownload(nonempty);
        assertThrows(IOException.class, () -> nonemptyDownload.run(target));
        assertTrue(empty.closed && empty.disconnected);
        assertTrue(nonempty.closed && nonempty.disconnected);
        assertArrayEquals(VALID, Files.readAllBytes(child));
        assertEquals(Set.of(target), files());
    }

    @Test
    void deletionFailureIsSuppressedOnTransferFailure() throws Exception {
        Path target = Files.write(directory.resolve("dependency.jar"), VALID);
        IOException transferFailure = new IOException("transfer failed");
        IOException cleanupFailure = new IOException("deletion failed");
        TestConnection connection = new TestConnection(new ByteArrayInputStream(REPLACEMENT) {
            @Override
            public long transferTo(OutputStream output) throws IOException {
                output.write(REPLACEMENT[0]);
                throw transferFailure;
            }
        });
        DownloadInvocation download = prepareDownload(connection);
        try (var ignored = mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("deleteIfExists")) {
                throw cleanupFailure;
            }
            return invocation.callRealMethod();
        })) {
            assertSame(transferFailure, assertThrows(IOException.class, () -> download.run(target)));
            assertArrayEquals(new Throwable[]{cleanupFailure}, transferFailure.getSuppressed());
        }
        assertTrue(connection.closed);
        assertTrue(connection.disconnected);
        assertArrayEquals(VALID, Files.readAllBytes(target));
    }

    private Set<Path> files() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.collect(Collectors.toSet());
        }
    }

    private static DownloadInvocation prepareDownload(TestConnection connection) throws Exception {
        Class<?> artifactType = Class.forName(JavascriptEngineHandler.class.getName() + "$MavenArtifact");
        var constructor = artifactType.getDeclaredConstructor(String.class, String.class, String.class);
        constructor.setAccessible(true);
        Object artifact = constructor.newInstance("example", "dependency", "1");
        var getUrl = artifactType.getDeclaredMethod("getMavenCentralJarUrl");
        getUrl.setAccessible(true);
        String repositoryUrl = (String) getUrl.invoke(artifact);
        var download = JavascriptEngineHandler.class.getDeclaredMethod("downloadArtifact", artifactType, Path.class);
        download.setAccessible(true);
        URL url = URL.of(URI.create(repositoryUrl), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL ignored) {
                return connection;
            }
        });
        URI uri = mock(URI.class);
        when(uri.toURL()).thenReturn(url);
        return target -> {
            try (var uris = mockStatic(URI.class, CALLS_REAL_METHODS)) {
                uris.when(() -> URI.create(repositoryUrl)).thenReturn(uri);
                try {
                    download.invoke(new JavascriptEngineHandler(), artifact, target);
                } catch (InvocationTargetException failure) {
                    if (failure.getCause() instanceof Exception cause) {
                        throw cause;
                    }
                    throw (Error) failure.getCause();
                }
            }
        };
    }

    @FunctionalInterface
    private interface DownloadInvocation {
        void run(Path target) throws Exception;
    }

    private static final class TestConnection extends HttpURLConnection {
        private final InputStream body;
        private boolean closed;
        private boolean disconnected;

        TestConnection(InputStream body) {
            super(null);
            this.body = body;
        }

        @Override
        public int getResponseCode() {
            return HTTP_OK;
        }

        @Override
        public InputStream getInputStream() {
            return new FilterInputStream(body) {
                @Override
                public long transferTo(OutputStream output) throws IOException {
                    return body.transferTo(output);
                }

                @Override
                public void close() throws IOException {
                    closed = true;
                    super.close();
                }
            };
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }
}
