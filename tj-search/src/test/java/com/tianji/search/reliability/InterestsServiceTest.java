package com.tianji.search.reliability;

import com.tianji.common.utils.UserContext;
import com.tianji.search.domain.po.Interests;
import com.tianji.search.service.impl.InterestsServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterestsServiceTest {
 private InterestsServiceImpl service;
 @BeforeEach void setup(){service=spy(new InterestsServiceImpl());UserContext.setUser(7L);}
 @AfterEach void cleanup(){UserContext.removeUser();}
 @Test void missingAndBlankPreferencesRemainNormalEmptyResults(){
  doReturn(null).when(service).getById(7L);assertEquals(List.of(),service.queryMyInterestsIds());
  doReturn(new Interests().setInterests(" ")).when(service).getById(7L);assertEquals(List.of(),service.queryMyInterestsIds());
 }
 @Test void validPreferencesAreParsed(){doReturn(new Interests().setInterests("12,34")).when(service).getById(7L);assertEquals(List.of(12L,34L),service.queryMyInterestsIds());}
 @Test void corruptPreferencesAreNotHiddenAsEmpty(){for(String value:List.of("bad","12,",",")){doReturn(new Interests().setInterests(value)).when(service).getById(7L);assertThrows(NumberFormatException.class,service::queryMyInterestsIds);}}
 @Test void savedPreferencesUseTheSameNormalizedValue(){
  doReturn(true).when(service).saveOrUpdate(any(Interests.class));service.saveInterests(Arrays.asList(12L,null,34L));
  var saved=ArgumentCaptor.forClass(Interests.class);verify(service).saveOrUpdate(saved.capture());assertEquals("12,34",saved.getValue().getInterests());assertEquals(7L,saved.getValue().getId());
 }
 @Test void clearingPreferencesStillDeletesTheRow(){doReturn(true).when(service).removeById(7L);service.saveInterests(List.of());verify(service).removeById(7L);}
}
