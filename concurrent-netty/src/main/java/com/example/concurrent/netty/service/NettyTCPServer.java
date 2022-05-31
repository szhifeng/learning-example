package com.example.concurrent.netty.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

/**
 * netty 中 channel 通道的类型：
 * NioSocketChannel, 代表异步的客户端 TCP Socket 连接.
 * NioServerSocketChannel, 异步的服务器端 TCP Socket 连接.
 * NioDatagramChannel, 异步的 UDP 连接
 * NioSctpChannel, 异步的客户端 Sctp 连接.
 * NioSctpServerChannel, 异步的 Sctp 服务器端连接.
 * OioSocketChannel, 同步的客户端 TCP Socket 连接.
 * OioServerSocketChannel, 同步的服务器端 TCP Socket 连接.
 * OioDatagramChannel, 同步的 UDP 连接
 * OioSctpChannel, 同步的 Sctp 服务器端连接.
 * OioSctpServerChannel, 同步的客户端 TCP Socket 连接
 *
 * @author szf
 * @describe: netty 服务
 * @Date 2022/5/27 14:44
 */
@Slf4j
@Service
public class NettyTCPServer {
    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;

    public NettyTCPServer() {
        //创建两个线程组 boosGroup、workerGroup
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    /**
     * 启动并绑定 端口
     */
    public void bind(int tcp, int socket) throws Exception {
        //创建 异步的服务器端 TCP Socket 服务端的启动对象，设置参数
        ServerBootstrap device = new ServerBootstrap();
        //设置两个线程组boosGroup和workerGroup
        device.group(bossGroup, workerGroup)
                //设置服务端通道实现类型(异步的服务器端 TCP Socket 连接)
                .channel(NioServerSocketChannel.class)
                //初始化服务端可连接队列,指定了队列的大小128
                .option(ChannelOption.SO_BACKLOG, 1024)
                //设置保持活动连接状态
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                //设置缓存大小
                .childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
                //设置是否延迟
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                // 使用匿名内部类的形式初始化通道对象，设置绑定客户端连接时候触发操作
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel sh) {
                        InetSocketAddress address = sh.remoteAddress();
                        log.info("TCP 客户端IP:" + address.getAddress() + ":" + address.getPort());
                        //给pipeline管道设置 处理器（业务操作在此处理）
                        sh.pipeline()
                                .addLast(new FixedLengthFrameDecoder(10))
                                .addLast("HeartBeat", new HeartBeatHandler());
                    }
                });
        //绑定监听端口，调用sync同步阻塞方法等待绑定操作完成，完成后返回ChannelFuture类似于JDK中Future

        ServerBootstrap webSocket = new ServerBootstrap();
        webSocket.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                //初始化服务端可连接队列,指定了队列的大小128
                .option(ChannelOption.SO_BACKLOG, 1024)
                //保持长连接
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                // 绑定客户端连接时候触发操作
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel sh) throws Exception {
                        InetSocketAddress address = sh.remoteAddress();
                        log.info("WEB SOCKET客户端IP:" + address.getAddress() + ":" + address.getPort());
                        sh.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new ChunkedWriteHandler())
                                .addLast(new HttpObjectAggregator(65535))
                                .addLast(new WebSocketServerProtocolHandler("/ws", "WebSocket", true, 65535))
                                .addLast(new WebSocketHandler());
                    }
                });
        //绑定监听端口，调用sync同步阻塞方法等待绑定操作完成，完成后返回ChannelFuture类似于JDK中Future
        ChannelFuture futureDevice = device.bind(tcp).sync();
        ChannelFuture futureWebSocket = webSocket.bind(socket).sync();
        if (futureDevice.isSuccess()) {
            log.info("TCP 服务端启动成功");
        } else {
            log.info("TCP 服务端启动失败");
            futureDevice.cause().printStackTrace();
            bossGroup.shutdownGracefully(); //关闭线程组
            workerGroup.shutdownGracefully();
        }
        if (futureWebSocket.isSuccess()) {
            log.info("WEB-SOCKET服务端启动成功");
        } else {
            log.info("WEB-SOCKET服务端启动失败");
            futureWebSocket.cause().printStackTrace();
            bossGroup.shutdownGracefully(); //关闭线程组
            workerGroup.shutdownGracefully();
        }
        //成功绑定到端口之后,给channel增加一个 管道关闭的监听器并同步阻塞,直到channel关闭,线程才会往下执行,结束进程。
        futureDevice.channel().closeFuture().sync();
        futureWebSocket.channel().closeFuture().sync();
    }

    /**
     * 端口解绑
     */
    public void unbind() {
        if (null != bossGroup && !bossGroup.isShutdown()) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (null != workerGroup && !workerGroup.isShutdown()) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }
}
