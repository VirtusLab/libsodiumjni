package libsodiumjni.tests;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

/**
 * Runs the native methods with the JVM's JNI checks enabled (-Xcheck:jni, see JniCheckChild), and
 * fails on any JNI misuse they report. Most of them are only warnings (e.g. a JNI call made while an
 * exception may be pending), which don't make the JVM fail, hence the check of its output.
 */
public class JniCheckTests {

    @Test
    public void nativeMethodsPassJniChecks() throws Exception {
        ChildJvm.Result result = ChildJvm.run(List.of("-Xcheck:jni"), JniCheckChild.class, List.of(), Map.of(), null);
        assertTrue(
                "Child JVM didn't complete (exit code " + result.exitCode + "):\n" + result.output,
                result.exitCode == 0 && result.output.contains("DONE"));
        // e.g. "WARNING in native method: JNI call made without checking exceptions when required to"
        List<String> jniIssues = result.output.lines()
                .filter(line -> line.contains("in native method"))
                .collect(Collectors.toList());
        assertTrue(
                "-Xcheck:jni reported JNI misuse: " + jniIssues + "\nChild JVM output:\n" + result.output,
                jniIssues.isEmpty());
    }
}
