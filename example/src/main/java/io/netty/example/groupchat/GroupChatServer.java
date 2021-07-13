package io.netty.example.groupchat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class GroupChatServer {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(GroupChatServer.class);


    private int port; //监听端口

    public GroupChatServer(int port) {
        this.port = port;
    }

    //编写run方法，处理客户端的请求
    public void run() throws  Exception{
        //创建BossGroup和WorkGroup是两个线程池，里面有多个NioEventGroup(实际上是线程)，线程组bossGroup和workerGroup, 含有的子线程NioEventLoop的个数默认为cpu核数的两倍
        // bossGroup只是处理连接请求 ,真正的和客户端业务处理，会交给workerGroup完成
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);//BossGroup相当于mainReactor，负责建立连接并且把连接注册到WorkGroup中
        logger.info("bossGroup 创建完成-相当于mainReactor");
        EventLoopGroup workerGroup = new NioEventLoopGroup(); //CPU核数的2倍 WorkGroup负责处理连接对应的读写事件。
        logger.info("workerGroup 创建完成-负责处理连接对应的读写事件");

        try {
            //创建服务器端的启动对象 netty为 ServerBootstrap java原生为 ServerSocketChannel
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            //使用链式编程来配置参数 调查完成返回对象本身
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)//使用NioServerSocketChannel作为服务器的通道实现 NioServerSocketChannel： 异步非阻塞的服务器端 TCP Socket 连接。
                    // 初始化服务器连接队列大小，服务端处理客户端连接请求是顺序处理的,所以同一时间只能处理一个客户端连接。
                    // 多个客户端同时来的时候,服务端将不能处理的客户端连接请求放在队列中等待处理
                    .option(ChannelOption.SO_BACKLOG, 128)//option()设置的是服务端用于接收进来的连接，也就是boosGroup线程。 SO_BACKLOG Socket参数，服务端接受连接的队列长度，如果队列已满，客户端连接将被拒绝。默认值，Windows为200，其他为128。
                    .childOption(ChannelOption.SO_KEEPALIVE, true)//客户端配置 childOption()是提供给父管道接收到的连接，也就是workerGroup线程。
                    //处理I / O事件或拦截I / O操作，然后将其转发到其{@link ChannelPipeline}中的下一个处理程序。
                    //使用匿名内部类的形式初始化通道对象 //
                    .childHandler(new ChannelInitializer<SocketChannel>() {//创建通道初始化对象，设置初始化参数 在Bootstrap中childHandler()方法需要初始化通道，实例化一个ChannelInitializer，这时候需要重写initChannel()初始化通道的方法，装配流水线就是在这个地方进行。代码演示如下：
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {//TODO 处理器Handler主要分为两种：ChannelInboundHandlerAdapter(入站处理器)、ChannelOutboundHandler(出站处理器)
                            //TODO 入站指的是数据从底层java NIO Channel到Netty的Channel。
                            //TODO 出站指的是通过Netty的Channel来操作底层的java NIO Channel。
                            //获取到 pipeline
                            ChannelPipeline pipeline = ch.pipeline();//ChannelPipeline是Netty处理请求的责任链，ChannelHandler则是具体处理请求的处理器。实际上每一个channel都有一个处理器的流水线。
                            //向pipeline加入解码器
                            pipeline.addLast("decoder", new StringDecoder());
                            //向pipeline加入编码器
                            pipeline.addLast("encoder", new StringEncoder());
                            //对workerGroup的SocketChannel设置处理器
                            pipeline.addLast(new GroupChatServerHandler());
                        }
                    });//给workerGroup的EventLoop对应的管道设置处理器

            System.out.println("netty 服务器启动");
            //绑定一个端口并且同步, 生成了一个ChannelFuture异步对象，通过isDone()等方法可以判断异步事件的执行情况
            //启动服务器(并绑定端口)，bind是异步操作，sync方法是等待异步操作执行完毕
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            //给cf注册监听器，监听我们关心的事件
            /*channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (cf.isSuccess()) {
                        System.out.println("监听端口9000成功");
                    } else {
                        System.out.println("监听端口9000失败");
                    }
                }
            });*/
            //对通道关闭进行监听，closeFuture是异步操作，监听通道关闭
            // 通过sync方法同步等待通道关闭处理完毕，这里会阻塞等待通道关闭完成
            channelFuture.channel().closeFuture().sync();
        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

    public static void main(String[] args) throws Exception {
        new GroupChatServer(8888).run();
    }
}
