package com.example.tcptestapplication
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.Exception
import java.net.*
import java.nio.charset.Charset
import java.util.logging.Logger

// adb reverse tcp:8000 tcp:8000
// adb forward tcp:8001 tcp:8001

open class TinyTCPUtil(open val address: String,open val port: Int){
    protected open lateinit var job: Job
    protected open var txChannel : Channel<String> = Channel(16)
    protected open var rxChannel : Channel<String> = Channel(16)

    protected open var bufferedReader : BufferedReader? = null
    protected open var bufferedWriter : BufferedWriter?= null
    protected open var client: Socket?= null

    open fun start(){
        stop()
        //その後各オープン処理をする
    }
    open fun stop(){
        //ブロッキングコルーチンで実行
        runBlocking {
            if(::job.isInitialized){
                //キャンセルを発行し、終わるまで待つ
                job.cancelAndJoin()
            }
        }
    }

    open suspend fun processClient(client: Socket?){
        //接続に成功したので通信タイムアウトを設定
        client?.soTimeout = 10
        //UTF-8でストリームを生成(だいたい今これで問題ないと思う)
        bufferedReader = BufferedReader(
            InputStreamReader(
                client?.getInputStream(),
                Charset.forName("UTF-8")
            )
        )
        bufferedWriter = BufferedWriter(
            OutputStreamWriter(
                client?.getOutputStream(),
                Charset.forName("UTF-8")
            )
        )

        //コルーチンがキャンセルされるまで実行を続ける
        while (client != null) {
            //他のコルーチンに時間を回したい
            delay(1)
            try {
                //送信要求があるなら
                while (!txChannel.isEmpty) {
                    clientWrite()
                }
                //受信データが有るなら
                try {
                    clientRead()
                } catch (e: SocketTimeoutException) {
                    continue
                }
            } catch (e: SocketException){
                Log.println(Log.ERROR,"TinyTCPUtil",e.toString())
                break
            }
        }
    }

    //IOスレッドでブロッキング処理を休止処理に変更
    open suspend fun clientRead() = withContext(Dispatchers.IO){
        //キャンセルチェック
        yield()
        //取り出す
        val msg: String = bufferedReader!!.readLine() ?: throw SocketException()//切断されたら抜ける
        rxChannel.send(msg)
        Log.println(Log.INFO,"TinyTCPUtil", "Receive: $msg")
    }
    //IOスレッドでブロッキング処理を休止処理に変更
    open suspend fun clientWrite() = withContext(Dispatchers.IO){
        //キャンセルチェック
        yield()
        //取り出して連続送信する
        val msg = txChannel.receive()
        bufferedWriter?.write(msg)
        //即座に送信する
        bufferedWriter?.flush()
        Log.println(Log.INFO,"TinyTCPUtil", "Send: $msg")
    }

    open fun isConnected():Boolean{
        return (client != null)
    }

    //バッファ済みの情報を取り出す
    open fun receive():String?{
        if(rxChannel.isEmpty){
            return null
        }
        return runBlocking {
            rxChannel.receive()
        }
    }

    //送信バッファに突っ込む
    open fun send(msg:String){
        runBlocking {
            txChannel.send(msg)
        }
    }
}

class TinyTCPUtilServer(override val address: String,override val port: Int) : TinyTCPUtil(address, port) {
    private var server: ServerSocket?= null

    override fun start(){
        super.start()

        //バックグラウンドコルーチンを開始する
        //IOスレッドで実行する
        job = GlobalScope.launch(Dispatchers.IO) {
            //バックグラウンド処理を開始
            try {
                //サーバーソケットを開始
                server = ServerSocket()
                //待受タイムアウトは1000ms
                server?.soTimeout = 1000
                server?.reuseAddress = true
                //ポート番号を割り当て
                server?.bind(InetSocketAddress(port))

                //コルーチンがアクティブの間実行
                while(isActive) {
                    //接続を待機
                    Log.println(Log.INFO, "TinyTCPUtilServer", "Waiting for connect")
                    try {
                        client = server?.accept()
                    } catch (e: SocketTimeoutException) {
                        //タイムアウトの場合は続行(それ以外は外へ投げる)
                        continue
                    }

                    //複数クライアントの接続を前提とした処理ではない。
                    processClient(client)
                }
            }catch (e:Exception){
                Log.println(Log.ERROR,"TinyTCPUtilServer",e.toString())
            }
            finally {
                //終了処理をキャンセル不可で実行
                withContext(NonCancellable){
                    bufferedReader?.close()
                    bufferedWriter?.close()
                    client?.close()
                    server?.close()
                    client = null
                    server = null
                }
            }
        }

    }
}

class TinyTCPUtilClient(override val address: String,override val port: Int):TinyTCPUtil(address,port) {
    override fun start(){
        stop()

        //バックグラウンドコルーチンを開始する
        //IOスレッドで実行する
        job = GlobalScope.launch(Dispatchers.IO) {
            //バックグラウンド処理を開始
            try {
                //ソケットを作成し、タイムアウトを設定
                client = Socket()
                client?.soTimeout = 1000

                //接続を開始する。
                try {
                    client?.connect(InetSocketAddress(address,port),1000)
                }catch (e: SocketTimeoutException){
                    Log.println(Log.ERROR,"TinyTCPUtilClient",e.toString())
                    return@launch
                }
                Log.println(Log.ERROR,"TinyTCPUtilClient","Connected")

                processClient(client)
            }catch (e:Exception){
                Log.println(Log.ERROR,"TinyTCPUtilClient",e.toString())
            }
            finally {
                //終了処理をキャンセル不可で実行
                withContext(NonCancellable){
                    bufferedReader?.close()
                    bufferedWriter?.close()
                    client?.close()
                    client = null
                }
            }
        }
    }
}