package com.bizard.homesmokemqtt;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSocketFactory;

/** Small MQTT 3.1.1 client used to keep the APK dependency-free. */
final class MqttClient {
    interface MessageListener { void onMessage(String topic,String payload); }
    private final String host,username,password;
    private final int port;
    private final boolean tls;
    private final AtomicInteger packetId=new AtomicInteger(1);
    private String clientId="HomeSmoke_"+Long.toHexString(System.nanoTime());
    private String willTopic="",willPayload="";
    private boolean willRetain=true;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected;
    private volatile MessageListener listener;
    private Thread reader,keepAlive;
    private volatile long lastPacketAt;

    MqttClient(String host,int port,boolean tls,String username,String password){this.host=host;this.port=port;this.tls=tls;this.username=username==null?"":username;this.password=password==null?"":password;}
    void setMessageListener(MessageListener listener){this.listener=listener;}
    void setClientId(String id){if(id!=null&&!id.trim().isEmpty())clientId=id.trim();}
    void setWill(String topic,String payload,boolean retain){willTopic=topic==null?"":topic.trim();willPayload=payload==null?"":payload;willRetain=retain;}

    synchronized void connect() throws IOException {
        close();
        socket=tls?SSLSocketFactory.getDefault().createSocket():new Socket();
        socket.connect(new InetSocketAddress(host,port),10000);socket.setSoTimeout(15000);
        in=socket.getInputStream();out=socket.getOutputStream();
        ByteArrayOutputStream b=new ByteArrayOutputStream();writeUtf(b,"MQTT");b.write(4);
        int flags=0x02;
        if(!willTopic.isEmpty()){flags|=0x04;if(willRetain)flags|=0x20;}
        if(!username.isEmpty())flags|=0x80;if(!password.isEmpty())flags|=0x40;
        b.write(flags);b.write(0);b.write(30);writeUtf(b,clientId);
        if(!willTopic.isEmpty()){writeUtf(b,willTopic);writeUtf(b,willPayload);}
        if(!username.isEmpty())writeUtf(b,username);if(!password.isEmpty())writeUtf(b,password);
        sendPacket(0x10,b.toByteArray());
        int h=in.read();if(h!=0x20)throw new IOException("нет CONNACK");int n=readRemaining(in);byte[] ack=readFully(in,n);
        if(ack.length<2||ack[1]!=0)throw new IOException("CONNACK="+(ack.length>1?ack[1]&255:-1));
        socket.setSoTimeout(0);connected=true;lastPacketAt=System.currentTimeMillis();startReader();startKeepAlive();
    }

    boolean isConnected(){Socket s=socket;return connected&&s!=null&&s.isConnected()&&!s.isClosed();}
    long getLastPacketAt(){return lastPacketAt;}

    synchronized void publish(String topic,String payload,boolean retain)throws IOException{publish(topic,payload,retain,0);}
    synchronized void publish(String topic,String payload,boolean retain,int qos)throws IOException{
        if(!isConnected())throw new IOException("не подключен");if(topic==null||topic.trim().isEmpty())throw new IOException("пустой topic");
        qos=qos<=0?0:1;ByteArrayOutputStream b=new ByteArrayOutputStream();writeUtf(b,topic.trim());
        if(qos==1){int id=nextPacketId();b.write((id>>>8)&255);b.write(id&255);}b.write(payload.getBytes(StandardCharsets.UTF_8));
        sendPacket(0x30|(qos<<1)|(retain?1:0),b.toByteArray());
    }

    synchronized void subscribe(String topic)throws IOException{
        if(!isConnected())throw new IOException("не подключен");if(topic==null||topic.trim().isEmpty())throw new IOException("пустой topic");
        ByteArrayOutputStream b=new ByteArrayOutputStream();int id=nextPacketId();b.write((id>>>8)&255);b.write(id&255);writeUtf(b,topic.trim());b.write(1);sendPacket(0x82,b.toByteArray());
    }

    private int nextPacketId(){return packetId.getAndUpdate(v->v>=65535?1:v+1);}
    private void startReader(){reader=new Thread(()->{try{while(connected){int h=in.read();if(h<0)throw new EOFException();int n=readRemaining(in);byte[] body=readFully(in,n);lastPacketAt=System.currentTimeMillis();if((h&0xF0)==0x30)handlePublish(h,body);}}catch(Exception e){close();}},"HomeSmoke-MQTT-reader");reader.start();}
    private void handlePublish(int header,byte[] body)throws IOException{
        if(body.length<2)return;int topicLen=((body[0]&255)<<8)|(body[1]&255);if(2+topicLen>body.length)return;
        String topic=new String(body,2,topicLen,StandardCharsets.UTF_8);int p=2+topicLen;int qos=(header>>1)&3;int incomingId=0;
        if(qos>0){if(p+2>body.length)return;incomingId=((body[p]&255)<<8)|(body[p+1]&255);p+=2;}
        String payload=new String(body,p,body.length-p,StandardCharsets.UTF_8);MessageListener l=listener;if(l!=null)try{l.onMessage(topic,payload);}catch(Exception ignored){}
        if(qos==1)sendPacket(0x40,new byte[]{(byte)(incomingId>>>8),(byte)incomingId});
    }
    private void startKeepAlive(){keepAlive=new Thread(()->{while(connected){try{Thread.sleep(20000);synchronized(MqttClient.this){if(connected)sendPacket(0xC0,new byte[0]);}}catch(Exception e){close();break;}}},"HomeSmoke-MQTT-keepalive");keepAlive.start();}
    private synchronized void sendPacket(int header,byte[] body)throws IOException{if(out==null)throw new IOException("socket закрыт");out.write(header);writeRemaining(out,body.length);out.write(body);out.flush();}
    synchronized void close(){connected=false;Socket s=socket;socket=null;if(s!=null)try{s.close();}catch(Exception ignored){}in=null;out=null;if(reader!=null&&reader!=Thread.currentThread())reader.interrupt();if(keepAlive!=null&&keepAlive!=Thread.currentThread())keepAlive.interrupt();reader=null;keepAlive=null;}
    private static void writeUtf(ByteArrayOutputStream out,String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);out.write((b.length>>>8)&255);out.write(b.length&255);out.write(b);}
    private static void writeRemaining(OutputStream out,int n)throws IOException{do{int d=n%128;n/=128;if(n>0)d|=0x80;out.write(d);}while(n>0);}
    private static int readRemaining(InputStream in)throws IOException{int m=1,v=0,d;do{d=in.read();if(d<0)throw new EOFException();v+=(d&127)*m;m*=128;if(m>268435456)throw new IOException("bad remaining length");}while((d&128)!=0);return v;}
    private static byte[] readFully(InputStream in,int n)throws IOException{byte[] b=new byte[n];int p=0;while(p<n){int r=in.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
}
