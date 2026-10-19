package com.tianji.gateway.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BackupStatusMetricsTest {
    @TempDir Path directory;
    private double gauge(SimpleMeterRegistry meters,String suffix){return meters.get("tj.backup."+suffix).gauge().value();}
    private void empty(SimpleMeterRegistry meters){
        assertEquals(0,gauge(meters,"last.success.timestamp.seconds"));assertEquals(0,gauge(meters,"last.failure.timestamp.seconds"));assertEquals(0,gauge(meters,"running"));
    }
    @Test void missingStateIsZeroAndDoesNotCreateTheStatusFile(){
        var file=directory.resolve("missing.properties");var meters=new SimpleMeterRegistry();
        assertDoesNotThrow(()->new BackupStatusMetrics(meters,file.toString()));empty(meters);assertFalse(Files.exists(file));
    }
    @Test void gaugesObserveTheAtomicallyReplacedNumericSnapshot()throws Exception {
        var file=directory.resolve("backup.properties");var meters=new SimpleMeterRegistry();var metrics=new BackupStatusMetrics(meters,file.toString());
        Files.writeString(file,"lastSuccess=1780000000\nlastFailure=1770000000\nrunning=1\n");metrics.refresh();
        assertEquals(1780000000,gauge(meters,"last.success.timestamp.seconds"));assertEquals(1770000000,gauge(meters,"last.failure.timestamp.seconds"));assertEquals(1,gauge(meters,"running"));
        var next=directory.resolve("next.properties");Files.writeString(next,"lastSuccess=1790000000\nlastFailure=1770000000\nrunning=0\n");
        Files.move(next,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);metrics.refresh();
        assertEquals(1790000000,gauge(meters,"last.success.timestamp.seconds"));assertEquals(0,gauge(meters,"running"));
        Files.delete(file);metrics.refresh();empty(meters);
    }
    @Test void corruptIncompleteOrAmbiguousStateCannotRetainAnEarlierSuccess()throws Exception {
        var file=directory.resolve("backup.properties");var meters=new SimpleMeterRegistry();var metrics=new BackupStatusMetrics(meters,file.toString());
        for(String invalid:List.of("lastSuccess=-1\nlastFailure=0\nrunning=0", "lastSuccess=NaN\nlastFailure=0\nrunning=0", "lastSuccess=9223372036854775808\nlastFailure=0\nrunning=0", "lastSuccess=1\nlastFailure=0\nrunning=2", "lastSuccess=1\nlastFailure=0", "lastSuccess=1\nlastFailure=0\nrunning=0\nother=1", "lastSuccess=1\nlastSuccess=2\nlastFailure=0\nrunning=0", "#".repeat(4097))){
            Files.writeString(file,"lastSuccess=1\nlastFailure=2\nrunning=1");metrics.refresh();assertEquals(1,gauge(meters,"last.success.timestamp.seconds"));
            Files.writeString(file,invalid);assertDoesNotThrow(metrics::refresh);empty(meters);
        }
    }
}
