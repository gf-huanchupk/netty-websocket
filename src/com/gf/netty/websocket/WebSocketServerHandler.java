package com.gf.netty.websocket;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
	
	private static final Logger logger = Logger.getLogger(WebSocketServerHandler.class.getName());
	
	private WebSocketServerHandshaker handshaker;
	
	public static List<ChannelHandlerContext> users = new ArrayList<ChannelHandlerContext>();

	@Override
	protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		// 传统 HTTP 接入
		if(msg instanceof FullHttpRequest){
			handeHttpRequest(ctx,(FullHttpRequest)msg);
		}
		
		// WebSocket 接入
		else if(msg instanceof WebSocketFrame){
			handeWebSocketFrame(ctx,(WebSocketFrame)msg);
		}
	}
	
	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}
	
    private void handeHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
    	// 如果 HTTP 解码失败 ， 返回 HTTP 异常， 并判断消息头有没有Upgrade字段（升级协议）
		if(!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))){
			sendHttpResponse(ctx,req,new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.BAD_REQUEST));
			return;
		}
		
		// 构建握手响应返回，本机测试
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory("ws://127.0.0.1:8081/websocket", null, false);
		handshaker = wsFactory.newHandshaker(req);
		
		if(handshaker == null){
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
			users.add(ctx);
		}
	}

	private void handeWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
		
		// 判断是否是关闭链路指令
		if(frame instanceof CloseWebSocketFrame){
			handshaker.close(ctx.channel(), (CloseWebSocketFrame)frame.retain());
			return;
		}
		
		// 判断是否是 Ping 消息
		if(frame instanceof PingWebSocketFrame){
			ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		
		// 本例仅支持文本消息，不支持二进制消息
		if(!(frame instanceof TextWebSocketFrame)){
			throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
		}
		
		// 返回应答消息
		String request = ((TextWebSocketFrame)frame).text();
		if(logger .isLoggable(Level.FINE)){
			logger.fine(String.format("%s received %S", ctx.channel(),request));
		}
		
		//------
		for (ChannelHandlerContext user : users) {
			user.channel().writeAndFlush(new TextWebSocketFrame(request + " , 欢迎使用Netty WebSocket 服务，现在时刻: " + new Date().toString()));
		}
		//------
		//ctx.channel().write(new TextWebSocketFrame(request + " , 欢迎使用Netty WebSocket 服务，现在时刻: " + new Date().toString()));
		
	}
	
	private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req,
			FullHttpResponse res) {
		// 返回应答给客户端
		if(res.status().code() != 200){
			ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(),CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpHeaderUtil.setContentLength(res,res.content().readableBytes());
		}
		
		// 如果是非Keep-Alive，关闭连接
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		
		if(HttpHeaderUtil.isKeepAlive(req) || res.status().code() != 200){
			f.addListener(ChannelFutureListener.CLOSE);
		}
		
	}
	
	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		System.out.println("---------------> 有通道关闭了~~");
		ctx.close();
		users.remove(ctx);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}
	
}
