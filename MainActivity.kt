package com.example.tcptestapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import org.w3c.dom.Text
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private lateinit var tinyTCPUtil : TinyTCPUtil
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            tinyTCPUtil = TinyTCPUtilServer("127.0.0.1",8001)
            //tinyTCPUtil = TinyTCPUtilClient("127.0.0.1",8000)
            tinyTCPUtil.start()
        }catch (e:Exception){
            println(e.stackTrace)
        }

        buttonGet.setOnClickListener {
            TextViewMsg.text = tinyTCPUtil.receive()
        }
        buttonSet.setOnClickListener {
            tinyTCPUtil.send("HelloWorld")
        }

        buttonConnect.setOnClickListener {
            tinyTCPUtil.start()
        }
        buttonDisconnect.setOnClickListener {
            tinyTCPUtil.stop()
        }

    }
}