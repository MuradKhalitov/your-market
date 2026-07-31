package ru.murad.yourmarket.config;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.*;import jakarta.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminApiKeyFilterTest {
 @Test void missingKeyIsUnauthorized()throws Exception{assertEquals(401,run(null));}
 @Test void wrongKeyIsUnauthorized()throws Exception{assertEquals(401,run("wrong"));}
 @Test void correctKeyPasses()throws Exception{assertEquals(200,run("secret"));}
 private int run(String key)throws Exception{var req=new MockHttpServletRequest("POST","/api/admin/advertisements/1/retry-publication");if(key!=null)req.addHeader("X-Admin-Api-Key",key);var res=new MockHttpServletResponse();FilterChain chain=(a,b)->((MockHttpServletResponse)b).setStatus(200);AdminProperties properties=new AdminProperties();properties.setApiKey("secret");new AdminApiKeyFilter(properties).doFilter(req,res,chain);return res.getStatus();}
}
