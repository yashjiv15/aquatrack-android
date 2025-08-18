package com.example.aquatrack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        findViewById<Button>(R.id.buttonProduction).setOnClickListener {
            startActivity(Intent(this, ProductionActivity::class.java))
        }
        findViewById<Button>(R.id.buttonStock).setOnClickListener {
            startActivity(Intent(this, StockActivity::class.java))
        }
        findViewById<Button>(R.id.buttonTester).setOnClickListener {
            startActivity(Intent(this, TesterActivity::class.java))
        }
        findViewById<Button>(R.id.buttonAccount).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }
    }
}

