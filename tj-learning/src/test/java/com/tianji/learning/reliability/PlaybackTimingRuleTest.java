package com.tianji.learning.reliability;

import com.tianji.learning.service.impl.PlaybackTimingService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackTimingRuleTest {
    @Test void firstHeartbeatAndDisconnectedIntervalsOnlyEstablishBaseline(){
        assertEquals(0,credit(0,10_000,10_000,1,"PLAYING","PLAYING").creditedMillis());
        assertEquals(0,credit(1,30_001,30_001,1,"PLAYING","PLAYING").creditedMillis());
        assertEquals(0,credit(1,-1,100,1,"PLAYING","PLAYING").creditedMillis());
    }
    @Test void creditIsBoundByServerTimeAndNormalPositionIncrement(){
        assertEquals(new PlaybackTimingService.Credit(9_000,18_000),credit(1,9_000,18_000,2,"PLAYING","PLAYING"));
        assertEquals(new PlaybackTimingService.Credit(8_000,4_000),credit(1,10_000,4_000,.5,"PLAYING","PLAYING"));
        assertEquals(new PlaybackTimingService.Credit(10_000,10_000),credit(1,10_000,11_000,1,"PLAYING","PLAYING"));
    }
    @Test void seekPauseHiddenAndBufferingCannotCreateTime(){
        for(String state:new String[]{"PAUSED","HIDDEN","BUFFERING","SEEKING"})
            assertEquals(0,credit(1,10_000,10_000,1,state,"PLAYING").creditedMillis());
        assertEquals(0,credit(1,10_000,10_000,1,"PLAYING","SEEKING").creditedMillis());
        assertEquals(0,credit(1,1000,50_000,1,"PLAYING","PLAYING").creditedMillis());
        assertEquals(0,credit(1,1000,-1000,1,"PLAYING","PLAYING").creditedMillis());
    }
    @Test void stoppingACurrentlyPlayingVideoSavesItsTail(){
        assertEquals(1900,credit(1,1900,1900,1,"PLAYING","ENDED").creditedMillis());
        assertEquals(1900,credit(1,1900,1900,1,"PLAYING","PAUSED").creditedMillis());
    }
    private PlaybackTimingService.Credit credit(long seq,long elapsed,long delta,double rate,String before,String after){return PlaybackTimingService.credit(seq,elapsed,delta,rate,before,after);}
}
