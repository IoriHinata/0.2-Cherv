package com.markovchapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.markovchapter.ui.MarkovViewModel
import com.markovchapter.ui.MarkovApp
class MainActivity:ComponentActivity(){
 private val vm:MarkovViewModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{MarkovApp(vm)}}}
}
