package com.gf.netty.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

public class WebSocketServer {
	
	public void run(int port) throws Exception{
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup,workerGroup)
			.channel(NioServerSocketChannel.class)
			.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipelline = ch.pipeline();
					pipelline.addLast("http-codec",new HttpServerCodec());
					pipelline.addLast("aggregator",new HttpObjectAggregator(65536));
					ch.pipeline().addLast("http-chunked",new ChunkedWriteHandler());
					pipelline.addLast("handler",new WebSocketServerHandler());
				}
			});
			
			Channel ch = b.bind(port).channel();
			
			System.out.println("Web socket server started at port " + port + ".");
			System.out.println("Open your brower and naviagte to http://localhsot:" + port + "/");
			
			ch.closeFuture().sync();
			
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		int port = 8081;
		if(args.length > 0){
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		
		new WebSocketServer().run(port);
	}

}
