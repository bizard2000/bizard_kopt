package com.bizard.homesmokemqtt.legacy;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import javax.net.ssl.SSLSocketFactory;

/** Android 4 compatible MQTT 3.1.1 subset. TLS availability depends on the tablet firmware. */
final class MqttClient {
    interface MessageListener{void onMessage(String topic,String payload);}
    private static final Charset UTF8=Charset.forName("UTF-8");
    private final String host,user,pass;private final int port;private final boolean tls;private int packetId=1;private Socket socket;private InputStream in;private OutputStream out;private volatile boolean connected;private volatile MessageListener listener;private Thread reader,keepAlive;
    MqttClient(String host,int port,boolean tls,String user,String pass){this.host=host;this.port=port;this.tls=tls;this.user=user==null?"":user;this.pass=pass==null?"":pass;}
    void setMessageListener(MessageListener l){listener=l;}
    synchronized void connect()throws IOException{close();socket=tls?SSLSocketFactory.getDefault().createSocket():new Socket();socket.connect(new InetSocketAddress(host,port),10000);socket.setSoTimeout(15000);in=socket.getInputStream();out=socket.getOutputStream();ByteArrayOutputStream b=new ByteArrayOutputStream();utf(b,"MQTT");b.write(4);int flags=2;if(user.length()>0)flags|=0x80;if(pass.length()>0)flags|=0x40;b.write(flags);b.write(0);b.write(30);utf(b,"HomeSmokeLegacy_"+Long.toHexString(System.nanoTime()));if(user.length()>0)utf(b,user);if(pass.length()>0)utf(b,pass);send(0x10,b.toByteArray());int h=in.read();if(h!=0x20)throw new IOException("нет CONNACK");byte[] ack=full(in,remaining(in));if(ack.length<2||ack[1]!=0)throw new IOException("CONNACK error");socket.setSoTimeout(0);connected=true;startReader();startKeepAlive();}
    boolean isConnected(){Socket s=socket;return connected&&s!=null&&s.isConnected()&&!s.isClosed();}
    synchronized void publish(String topic,String payload,boolean retain)throws IOException{if(!isConnected())throw new IOException("не подключен");ByteArrayOutputStream b=new ByteArrayOutputStream();utf(b,topic);b.write(payload.getBytes(UTF8));send(0x30|(retain?1:0),b.toByteArray());}
    synchronized void subscribe(String topic)throws IOException{if(!isConnected())throw new IOException("не подключен");ByteArrayOutputStream b=new ByteArrayOutputStream();int id=nextId();b.write(id>>>8);b.write(id);utf(b,topic);b.write(1);send(0x82,b.toByteArray());}
    private synchronized int nextId(){int v=packetId;packetId=packetId>=65535?1:packetId+1;return v;}
    private void startReader(){reader=new Thread(new Runnable(){public void run(){try{while(connected){int h=in.read();if(h<0)throw new EOFException();byte[] body=full(in,remaining(in));if((h&0xF0)==0x30)handle(h,body);}}catch(Exception e){close();}}},"HomeSmokeLegacy-MQTT-reader");reader.start();}
    private void handle(int header,byte[] body)throws IOException{if(body.length<2)return;int n=((body[0]&255)<<8)|(body[1]&255);if(2+n>body.length)return;String topic=new String(body,2,n,UTF8);int p=2+n;int qos=(header>>1)&3;int id=0;if(qos>0){if(p+2>body.length)return;id=((body[p]&255)<<8)|(body[p+1]&255);p+=2;}String payload=new String(body,p,body.length-p,UTF8);MessageListener l=listener;if(l!=null)try{l.onMessage(topic,payload);}catch(Exception ignored){}if(qos==1)send(0x40,new byte[]{(byte)(id>>>8),(byte)id});}
    private void startKeepAlive(){keepAlive=new Thread(new Runnable(){public void run(){while(connected)try{Thread.sleep(20000);synchronized(MqttClient.this){if(connected)send(0xC0,new byte[0]);}}catch(Exception e){close();break;}}},"HomeSmokeLegacy-MQTT-ping");keepAlive.start();}
    private synchronized void send(int h,byte[] body)throws IOException{if(out==null)throw new IOException("socket закрыт");out.write(h);writeRemaining(out,body.length);out.write(body);out.flush();}
    synchronized void close(){connected=false;Socket s=socket;socket=null;if(s!=null)try{s.close();}catch(Exception ignored){}in=null;out=null;if(reader!=null&&reader!=Thread.currentThread())reader.interrupt();if(keepAlive!=null&&keepAlive!=Thread.currentThread())keepAlive.interrupt();reader=null;keepAlive=null;}
    private static void utf(ByteArrayOutputStream o,String s)throws IOException{byte[] b=s.getBytes(UTF8);o.write(b.length>>>8);o.write(b.length);o.write(b);}private static void writeRemaining(OutputStream o,int n)throws IOException{do{int d=n%128;n/=128;if(n>0)d|=128;o.write(d);}while(n>0);}private static int remaining(InputStream i)throws IOException{int m=1,v=0,d;do{d=i.read();if(d<0)throw new EOFException();v+=(d&127)*m;m*=128;if(m>268435456)throw new IOException("bad length");}while((d&128)!=0);return v;}private static byte[] full(InputStream i,int n)throws IOException{byte[] b=new byte[n];int p=0;while(p<n){int r=i.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
}
