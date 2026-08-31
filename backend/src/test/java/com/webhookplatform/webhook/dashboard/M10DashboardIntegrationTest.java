package com.webhookplatform.webhook.dashboard;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.webhookplatform.webhook.security.AuthenticatedOidcUser;

@SpringBootTest(properties={"GOOGLE_CLIENT_ID=test-client-id","GOOGLE_CLIENT_SECRET=test-client-secret","webhook-platform.worker.enabled=false"})
@AutoConfigureMockMvc @Testcontainers @Import(M10DashboardIntegrationTest.FixedClockConfiguration.class)
class M10DashboardIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired FixedClock clock;
 @BeforeEach void clean(){for(String t:List.of("webhook_delivery_attempts","webhook_signing_secrets","webhook_deliveries","webhook_events","webhook_subscriptions","webhook_endpoints","api_keys","applications","users"))jdbc.update("DELETE FROM "+t);}
 @Test void sessionReadsAreOwnedPaginatedAndRedacted() throws Exception {
  UUID owner=user("owner"), other=user("other"), app=app(owner,"owner-app"), foreign=app(other,"other-app");
  UUID oldest=event(app,"old","ai.solution.completed",Instant.parse("2030-01-01T00:00:00Z"));
  UUID sameA=event(app,"same-a","ai.solution.completed",Instant.parse("2030-01-02T00:00:00Z"));
  UUID sameB=event(app,"same-b","ai.grade.completed",Instant.parse("2030-01-02T00:00:00Z"));
  UUID foreignEvent=event(foreign,"foreign","ai.solution.completed",Instant.parse("2030-01-03T00:00:00Z"));
  String base="/api/v1/applications/"+app;
  mvc.perform(get(base+"/dashboard/summary").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.events.total").value(3));
  var first=mvc.perform(get(base+"/events?size=2").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].payload").doesNotExist()).andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn();
  String cursor=new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString()).get("nextCursor").asText();
  mvc.perform(get(base+"/events?size=2&cursor="+cursor).with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").isEmpty());
  mvc.perform(get(base+"/events/"+sameA).with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.payload.status").value("ok")).andExpect(jsonPath("$.signingSecret").doesNotExist());
  mvc.perform(get(base+"/events/"+foreignEvent).with(authentication(auth(owner)))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/applications/"+foreign+"/events").with(authentication(auth(owner)))).andExpect(status().isNotFound());
  mvc.perform(get(base+"/events?size=101").with(authentication(auth(owner)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_QUERY"));
  mvc.perform(get(base+"/events?cursor=bad").with(authentication(auth(owner)))).andExpect(status().isBadRequest());
  mvc.perform(get(base+"/events?createdFrom=2030-01-03T00:00:00Z&createdTo=2030-01-01T00:00:00Z").with(authentication(auth(owner)))).andExpect(status().isBadRequest());
  mvc.perform(get(base+"/events?eventType=ai.solution.completed").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
  mvc.perform(get(base+"/events?eventType=ai.none.completed").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.nextCursor").isEmpty());
  mvc.perform(get(base+"/events?sourceEventId=same-a").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(sameA.toString()));
  mvc.perform(get(base+"/events?createdFrom=2030-01-02T00:00:00Z&createdTo=2030-01-02T00:00:00Z").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
  mvc.perform(get(base+"/events")).andExpect(status().isUnauthorized());
 }
 @Test void producerKeyCannotReadDashboardButRemainsValidForIngestion() throws Exception {
  UUID owner=user("producer-owner"), app=app(owner,"producer-app"); String raw="test-m10-producer-api-key"; apiKey(app,raw);
  String base="/api/v1/applications/"+app;
  mvc.perform(get(base+"/dashboard/summary").header("Authorization","Bearer "+raw)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/events").header("Authorization","Bearer "+raw).contentType(MediaType.APPLICATION_JSON).content("{\"sourceEventId\":\"producer-boundary\",\"eventType\":\"ai.solution.completed\",\"payload\":{\"status\":\"ok\"}}")).andExpect(status().isCreated());
 }
 @Test void summaryIsApplicationScopedAndUsesFixedTwentyFourHourWindow() throws Exception {
  clock.set(Instant.parse("2030-01-07T00:00:00Z")); UUID owner=user("summary-owner"), other=user("summary-other"), app=app(owner,"summary-app"), foreign=app(other,"summary-other-app");
  String[] statuses={"PENDING","PROCESSING","RETRY_SCHEDULED","DELIVERED","FAILED"};
  for(int i=0;i<statuses.length;i++){Instant at=i==0?Instant.parse("2030-01-05T23:59:59Z"):Instant.parse("2030-01-06T12:00:00Z"); UUID e=event(app,"summary-"+i,"ai.summary.completed",at); delivery(e,endpoint(app,"https://summary.test/"+i),statuses[i],at);}
  UUID foreignEvent=event(foreign,"foreign-summary","ai.summary.completed",Instant.parse("2030-01-06T12:00:00Z")); delivery(foreignEvent,endpoint(foreign,"https://foreign.test/hook"),"FAILED",Instant.parse("2030-01-06T12:00:00Z"));
  mvc.perform(get("/api/v1/applications/"+app+"/dashboard/summary").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.events.total").value(5)).andExpect(jsonPath("$.events.last24Hours").value(4)).andExpect(jsonPath("$.deliveries.pending").value(1)).andExpect(jsonPath("$.deliveries.processing").value(1)).andExpect(jsonPath("$.deliveries.retryScheduled").value(1)).andExpect(jsonPath("$.deliveries.delivered").value(1)).andExpect(jsonPath("$.deliveries.failed").value(1)).andExpect(jsonPath("$.recentFailures").value(1));
 }
 @Test void deliveryReadsFilterPaginateExposeAttemptsAndRedact() throws Exception {
  UUID owner=user("delivery-owner"), other=user("delivery-other"), app=app(owner,"delivery-app"), foreign=app(other,"delivery-other-app");
  UUID endpoint=endpoint(app,"https://owner.test/hook?token=hidden"), endpoint2=endpoint(app,"https://owner.test/hook-2"), endpoint3=endpoint(app,"https://owner.test/hook-3"), endpoint4=endpoint(app,"https://owner.test/hook-4"), endpoint5=endpoint(app,"https://owner.test/hook-5"), foreignEndpoint=endpoint(foreign,"https://other.test/hook");
  UUID pending=delivery(event(app,"delivery-pending","ai.solution.completed",Instant.parse("2030-01-02T00:00:00Z")),endpoint,"PENDING",Instant.parse("2030-01-02T01:00:00Z"));
  UUID processing=delivery(event(app,"delivery-processing","ai.solution.completed",Instant.parse("2030-01-03T00:00:00Z")),endpoint2,"PROCESSING",Instant.parse("2030-01-04T01:00:00Z"));
  UUID retry=delivery(event(app,"delivery-retry","ai.solution.completed",Instant.parse("2030-01-04T00:00:00Z")),endpoint3,"RETRY_SCHEDULED",Instant.parse("2030-01-04T01:00:00Z"));
  UUID delivered=delivery(event(app,"delivery-delivered","ai.grade.completed",Instant.parse("2030-01-05T00:00:00Z")),endpoint4,"DELIVERED",Instant.parse("2030-01-05T01:00:00Z"));
  UUID failed=delivery(event(app,"delivery-failed","ai.solution.completed",Instant.parse("2030-01-06T00:00:00Z")),endpoint5,"FAILED",Instant.parse("2030-01-06T01:00:00Z"));
  UUID foreignDelivery=delivery(event(foreign,"foreign-delivery","ai.solution.completed",Instant.parse("2030-01-06T00:00:00Z")),foreignEndpoint,"FAILED",Instant.parse("2030-01-06T01:00:00Z"));
  attempt(delivered,1,"FAILED",500,"HTTP_ERROR"); attempt(delivered,2,"ABANDONED",null,null); attempt(delivered,3,"SUCCEEDED",200,null);
  String base="/api/v1/applications/"+app;
  var first=mvc.perform(get(base+"/deliveries?size=2").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].id").isNotEmpty()).andExpect(jsonPath("$.items[0].eventId").isNotEmpty()).andExpect(jsonPath("$.items[0].eventType").isNotEmpty()).andExpect(jsonPath("$.items[0].endpointId").isNotEmpty()).andExpect(jsonPath("$.items[0].status").isNotEmpty()).andExpect(jsonPath("$.items[0].createdAt").isNotEmpty()).andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty()).andExpect(jsonPath("$.items[0].payload").doesNotExist()).andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn();
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper(); var firstBody=mapper.readTree(first.getResponse().getContentAsString()); String cursor=firstBody.get("nextCursor").asText();
  var second=mvc.perform(get(base+"/deliveries?size=2&cursor="+cursor).with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn();
  var secondBody=mapper.readTree(second.getResponse().getContentAsString()); var third=mvc.perform(get(base+"/deliveries?size=2&cursor="+secondBody.get("nextCursor").asText()).with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").isEmpty()).andReturn();
  Set<String> pageIds=new HashSet<>(); for(var body:List.of(firstBody,secondBody,mapper.readTree(third.getResponse().getContentAsString()))) for(var item:body.get("items")) pageIds.add(item.get("id").asText()); org.assertj.core.api.Assertions.assertThat(pageIds).containsExactlyInAnyOrder(pending.toString(),processing.toString(),retry.toString(),delivered.toString(),failed.toString());
  mvc.perform(get(base+"/deliveries?status=RETRY_SCHEDULED").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].nextRetryAt").isNotEmpty());
  mvc.perform(get(base+"/deliveries?status=FAILED&endpointId="+endpoint5+"&eventType=ai.solution.completed&createdFrom=2030-01-06T01:00:00Z&createdTo=2030-01-06T01:00:00Z").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(failed.toString()));
  mvc.perform(get(base+"/deliveries?status=INVALID").with(authentication(auth(owner)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_QUERY"));
  mvc.perform(get(base+"/deliveries?size=0").with(authentication(auth(owner)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_QUERY"));
  mvc.perform(get(base+"/deliveries?cursor=bad").with(authentication(auth(owner)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_QUERY"));
  mvc.perform(get(base+"/deliveries?createdFrom=2030-01-07T00:00:00Z&createdTo=2030-01-06T00:00:00Z").with(authentication(auth(owner)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_QUERY"));
  mvc.perform(get(base+"/deliveries?createdFrom=not-a-time").with(authentication(auth(owner)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST")).andExpect(jsonPath("$.stackTrace").doesNotExist());
  mvc.perform(get(base+"/deliveries/"+pending).with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.event.sourceEventId").value("delivery-pending")).andExpect(jsonPath("$.endpoint.id").value(endpoint.toString())).andExpect(jsonPath("$.targetUrl").value("https://owner.test/hook")).andExpect(jsonPath("$.claimToken").doesNotExist()).andExpect(jsonPath("$.signingSecret").doesNotExist());
  mvc.perform(get(base+"/deliveries/"+retry).with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETRY_SCHEDULED")).andExpect(jsonPath("$.nextRetryAt").isNotEmpty());
  mvc.perform(get(base+"/deliveries?status=DELIVERED").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].attemptCount").value(3)).andExpect(jsonPath("$.items[0].lastAttempt.attemptNumber").value(3)).andExpect(jsonPath("$.items[0].lastAttempt.status").value("SUCCEEDED")).andExpect(jsonPath("$.items[0].lastAttempt.httpStatusCode").value(200));
  mvc.perform(get(base+"/deliveries/"+delivered+"/attempts").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].attemptNumber").value(1)).andExpect(jsonPath("$[0].httpStatusCode").value(500)).andExpect(jsonPath("$[1].status").value("ABANDONED")).andExpect(jsonPath("$[2].httpStatusCode").value(200)).andExpect(jsonPath("$[0].claimToken").doesNotExist()).andExpect(jsonPath("$[0].authorization").doesNotExist()).andExpect(jsonPath("$[0].responseBody").doesNotExist()).andExpect(jsonPath("$[0].stackTrace").doesNotExist()).andExpect(jsonPath("$[0].signature").doesNotExist());
  mvc.perform(get(base+"/deliveries/"+foreignDelivery).with(authentication(auth(owner)))).andExpect(status().isNotFound());
  mvc.perform(get(base+"/deliveries/"+foreignDelivery+"/attempts").with(authentication(auth(owner)))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/applications/"+foreign+"/deliveries").with(authentication(auth(owner)))).andExpect(status().isNotFound());
  UUID emptyApp=app(owner,"empty-app"); mvc.perform(get("/api/v1/applications/"+emptyApp+"/deliveries").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.nextCursor").isEmpty());
  mvc.perform(get(base+"/deliveries/"+pending+"/attempts").with(authentication(auth(owner)))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
 }
 private UUID user(String s){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO users(id,google_subject,email,display_name,status,created_at,updated_at)VALUES(?,?,?,?,'ACTIVE',now(),now())",id,s,s+"@x.test",s);return id;}
 private UUID app(UUID owner,String slug){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO applications(id,owner_user_id,name,slug,status,environment,created_at,updated_at)VALUES(?,?,?,?,'ACTIVE','DEVELOPMENT',now(),now())",id,owner,"app",slug);return id;}
 private UUID event(UUID app,String source,String type,Instant at){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO webhook_events(id,application_id,source_event_id,event_type,payload,created_at)VALUES(?,?,?,?,'{\"status\":\"ok\"}'::jsonb,?)",id,app,source,type,java.sql.Timestamp.from(at));return id;}
 private UUID endpoint(UUID app,String url){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO webhook_endpoints(id,application_id,name,url,status,created_at,updated_at)VALUES(?,?, 'ep',?,'ACTIVE',now(),now())",id,app,url);return id;}
 private void apiKey(UUID application,String raw){try{String hash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));jdbc.update("INSERT INTO api_keys(id,application_id,name,key_prefix,key_hash,status,created_at)VALUES(?,?, 'producer','test',?,'ACTIVE',now())",UUID.randomUUID(),application,hash);}catch(Exception e){throw new IllegalStateException(e);}}
 private UUID delivery(UUID event,UUID endpoint,String status,Instant at){UUID id=UUID.randomUUID();Object retry=status.equals("RETRY_SCHEDULED")?java.sql.Timestamp.from(at.plusSeconds(60)):null;Object started=status.equals("PROCESSING")?java.sql.Timestamp.from(at):null;Object token=status.equals("PROCESSING")?UUID.randomUUID():null;jdbc.update("INSERT INTO webhook_deliveries(id,event_id,endpoint_id,target_url,status,next_retry_at,processing_started_at,claim_token,created_at,updated_at)VALUES(?,?,?,'https://owner.test/hook?token=hidden',?,?,?,?,?,?)",id,event,endpoint,status,retry,started,token,java.sql.Timestamp.from(at),java.sql.Timestamp.from(at));return id;}
 private void attempt(UUID delivery,int number,String status,Integer http,String error){UUID id=UUID.randomUUID(),claim=UUID.randomUUID();Instant now=Instant.parse("2030-01-05T02:00:00Z");jdbc.update("INSERT INTO webhook_delivery_attempts(id,delivery_id,attempt_number,claim_token,status,started_at,completed_at,duration_ms,http_status_code,error_code)VALUES(?,?,?,?,?,?,?,?,?,?)",id,delivery,number,claim,status,java.sql.Timestamp.from(now),status.equals("IN_PROGRESS")?null:java.sql.Timestamp.from(now),status.equals("ABANDONED")?null:1L,http,error);}
 private OAuth2AuthenticationToken auth(UUID id){OidcUser u=mock(OidcUser.class);java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> roles=List.of(new SimpleGrantedAuthority("ROLE_USER"));doReturn(roles).when(u).getAuthorities();when(u.getName()).thenReturn(id.toString());return new OAuth2AuthenticationToken(new AuthenticatedOidcUser(u,id,"x@test","x",null),roles,"google");}
 @TestConfiguration static class FixedClockConfiguration { @Bean @Primary FixedClock fixedClock(){return new FixedClock(Instant.parse("2030-01-07T00:00:00Z"));} }
 static final class FixedClock extends Clock { private volatile Instant now; FixedClock(Instant now){this.now=now;} void set(Instant value){now=value;} @Override public ZoneId getZone(){return ZoneOffset.UTC;} @Override public Clock withZone(ZoneId zone){return this;} @Override public Instant instant(){return now;} }
}
