package com.tianji.compact;
import feign.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.request.RequestContextHolder;
import com.tianji.common.utils.UserContext;
/** Typed Feign interfaces retain their contracts; same-host requests use the target module's MVC adapter. */
public final class LocalModuleCapability implements Capability {
 private final ModuleRegistry registry;
 private final Map<String,Semaphore> limits=new ConcurrentHashMap<>();
 public LocalModuleCapability(ModuleRegistry registry){this.registry=registry;}
 @Override public Client enrich(Client network){return (request,options)->{
  URI uri=URI.create(request.url());String path=uri.getPath();String prefix="/_modules/";
  if(!path.startsWith(prefix))return network.execute(request,options);
  String rest=path.substring(prefix.length());int slash=rest.indexOf('/');String alias=slash<0?rest:rest.substring(0,slash);
  var module=registry.get(alias);if(module==null)return network.execute(request,options);
  var limit=limits.computeIfAbsent(alias,a->new Semaphore(16));if(!limit.tryAcquire())throw new FeignException.TooManyRequests("Local module is busy",request,new byte[0],Map.of());
  try{return invoke(module,alias,request,uri);}finally{limit.release();}
 };}
 private Response invoke(ModuleRegistry.Module module,String alias,Request request,URI uri)throws IOException {
  if(request.body()!=null && request.body().length>16*1024*1024)throw new IOException("Local request exceeds 16 MiB");
  String configured=System.getenv("TJ_INTERNAL_TOKEN");String presented=request.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("X-Internal-Token")).flatMap(e->e.getValue().stream()).findFirst().orElse(null);
  if(configured==null || configured.length()<32 || presented==null || !java.security.MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),presented.getBytes(StandardCharsets.UTF_8)))return Response.builder().request(request).status(403).reason("Service identity required").headers(Map.of()).body(new byte[0]).build();
  var priorAttributes=RequestContextHolder.getRequestAttributes();Long user=UserContext.getUser(),role=UserContext.getRole();String session=UserContext.getSession();var mdc=org.slf4j.MDC.getCopyOfContextMap();
  var exchange=new Exchange(module,alias,request,uri);
  try{module.servlet().service(exchange.request(),exchange.response());exchange.writer.flush();return Response.builder().request(request).status(exchange.status).reason("").headers(exchange.headers).body(exchange.output.toByteArray()).build();}
  catch(ServletException error){throw new IOException("Local module invocation failed",error);}
  finally{UserContext.removeUser();if(user!=null)UserContext.setUser(user);if(role!=null)UserContext.setRole(role);if(session!=null)UserContext.setSession(session);if(priorAttributes==null)RequestContextHolder.resetRequestAttributes();else RequestContextHolder.setRequestAttributes(priorAttributes);if(mdc==null)org.slf4j.MDC.clear();else org.slf4j.MDC.setContextMap(mdc);}
 }
 static final class Exchange {
  final ModuleRegistry.Module module;final String alias;final Request source;final URI uri;
  final Map<String,Object> attributes=new HashMap<>();final Map<String,String[]> parameters=new LinkedHashMap<>();
  final Map<String,Collection<String>> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  final ByteArrayOutputStream output=new ByteArrayOutputStream(){@Override public synchronized void write(byte[] b,int off,int len){if(count+len>16*1024*1024)throw new IllegalStateException("Local response exceeds 16 MiB");super.write(b,off,len);}@Override public synchronized void write(int b){if(count>=16*1024*1024)throw new IllegalStateException("Local response exceeds 16 MiB");super.write(b);}};
  final PrintWriter writer=new PrintWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));int status=200;boolean committed;String encoding="UTF-8";
  Exchange(ModuleRegistry.Module module,String alias,Request source,URI uri){this.module=module;this.alias=alias;this.source=source;this.uri=uri;
   if(uri.getRawQuery()!=null)for(String pair:uri.getRawQuery().split("&")){String[] parts=pair.split("=",2);String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8),value=parts.length<2?"":URLDecoder.decode(parts[1],StandardCharsets.UTF_8);String[] old=parameters.getOrDefault(key,new String[0]);String[] values=Arrays.copyOf(old,old.length+1);values[old.length]=value;parameters.put(key,values);}
  }
  String header(String name){return source.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).flatMap(e->e.getValue().stream()).findFirst().orElse(null);}
  HttpServletRequest request(){return (HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},(proxy,method,args)->{
   String name=method.getName();String path=uri.getPath().substring(("/_modules/"+alias).length());byte[] bytes=source.body()==null?new byte[0]:source.body();
   return switch(name){
    case "getMethod"->source.httpMethod().name();case "getRequestURI"->uri.getPath();case "getRequestURL"->new StringBuffer(uri.toString().split("\\?",2)[0]);case "getServletPath"->"/_modules/"+alias;case "getPathInfo"->path;case "getContextPath"->"";case "getQueryString"->uri.getRawQuery();
    case "getProtocol"->"HTTP/1.1";case "getScheme"->"http";case "getServerName","getRemoteHost","getLocalName"->uri.getHost();case "getServerPort","getLocalPort"->uri.getPort()<0?80:uri.getPort();case "getRemoteAddr","getLocalAddr"->"127.0.0.1";case "getRemotePort"->0;
    case "getHeader"->header((String)args[0]);case "getHeaders"->Collections.enumeration(source.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase((String)args[0])).flatMap(e->e.getValue().stream()).toList());case "getHeaderNames"->Collections.enumeration(source.headers().keySet());case "getIntHeader"->header((String)args[0])==null?-1:Integer.parseInt(header((String)args[0]));case "getDateHeader"->-1L;
    case "getAttribute"->attributes.get((String)args[0]);case "getAttributeNames"->Collections.enumeration(attributes.keySet());case "setAttribute"->{attributes.put((String)args[0],args[1]);yield null;}case "removeAttribute"->{attributes.remove((String)args[0]);yield null;}
    case "getParameter"->parameters.containsKey(args[0])?parameters.get(args[0])[0]:null;case "getParameterValues"->parameters.get(args[0]);case "getParameterNames"->Collections.enumeration(parameters.keySet());case "getParameterMap"->Collections.unmodifiableMap(parameters);
    case "getContentType"->header("Content-Type");case "getCharacterEncoding"->encoding;case "setCharacterEncoding"->{encoding=(String)args[0];yield null;}case "getContentLength"->bytes.length;case "getContentLengthLong"->(long)bytes.length;case "getInputStream"->input(bytes);case "getReader"->new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8));
    case "getServletContext"->module.context().getServletContext();case "getDispatcherType"->DispatcherType.REQUEST;case "getLocale"->Locale.ROOT;case "getLocales"->Collections.enumeration(List.of(Locale.ROOT));case "isSecure","isAsyncStarted","isAsyncSupported","isRequestedSessionIdValid","isRequestedSessionIdFromCookie","isRequestedSessionIdFromURL","isTrailerFieldsReady"->false;
    case "getCookies","getUserPrincipal","getSession","getRequestedSessionId","getAuthType","getRemoteUser","getPathTranslated"->null;
    case "getHttpServletMapping"->new HttpServletMapping(){public String getMatchValue(){return path.replaceFirst("^/","");}public String getPattern(){return "/_modules/"+alias+"/*";}public String getServletName(){return "module-"+alias;}public MappingMatch getMappingMatch(){return MappingMatch.PATH;}};
    case "toString"->"LocalModuleRequest["+alias+"]";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];
    default->throw new UnsupportedOperationException("Internal transport does not support "+name);
   };
  });}
  HttpServletResponse response(){return (HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletResponse.class},(proxy,method,args)->switch(method.getName()){
   case "setStatus"->{status=(Integer)args[0];yield null;}case "getStatus"->status;case "sendError"->{status=(Integer)args[0];committed=true;yield null;}case "sendRedirect"->{status=302;headers.put("Location",List.of((String)args[0]));committed=true;yield null;}
   case "setHeader","setIntHeader","setDateHeader"->{if(args[1]==null)headers.remove((String)args[0]);else headers.put((String)args[0],new ArrayList<>(List.of(args[1].toString())));yield null;}case "addHeader","addIntHeader","addDateHeader"->{if(args[1]!=null)headers.computeIfAbsent((String)args[0],k->new ArrayList<>()).add(args[1].toString());yield null;}
   case "containsHeader"->headers.containsKey(args[0]);case "getHeader"->headers.containsKey(args[0])?headers.get(args[0]).iterator().next():null;case "getHeaders"->headers.getOrDefault(args[0],List.of());case "getHeaderNames"->headers.keySet();
   case "setContentType"->{if(args[0]==null)headers.remove("Content-Type");else headers.put("Content-Type",new ArrayList<>(List.of((String)args[0])));yield null;}case "getContentType"->headers.containsKey("Content-Type")?headers.get("Content-Type").iterator().next():null;
   case "setContentLength","setContentLengthLong","setBufferSize","setLocale","addCookie"->null;case "getCharacterEncoding"->encoding;case "setCharacterEncoding"->{encoding=(String)args[0];yield null;}case "getLocale"->Locale.ROOT;case "getBufferSize"->16*1024;
   case "getWriter"->writer;case "getOutputStream"->new ServletOutputStream(){public boolean isReady(){return true;}public void setWriteListener(WriteListener listener){throw new UnsupportedOperationException("Synchronous transport");}public void write(int b){output.write(b);}public void write(byte[] b,int off,int len){output.write(b,off,len);}};
   case "flushBuffer"->{writer.flush();committed=true;yield null;}case "isCommitted"->committed;case "reset","resetBuffer"->{output.reset();yield null;}case "encodeURL","encodeRedirectURL"->args[0];
   case "toString"->"LocalModuleResponse["+status+"]";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->throw new UnsupportedOperationException("Internal transport does not support "+method.getName());
  });}
  private ServletInputStream input(byte[] bytes){return new ServletInputStream(){private final ByteArrayInputStream input=new ByteArrayInputStream(bytes);public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous transport");}public int read(){return input.read();}public int read(byte[] b,int off,int len){return input.read(b,off,len);}};}
 }
}
