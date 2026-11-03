package ir.hesabyar.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.hesabyar.finance.ui.FinanceApp
import ir.hesabyar.finance.ui.FinanceViewModel
import ir.hesabyar.finance.ui.theme.HesabYarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HesabYarTheme {
                val vm: FinanceViewModel = viewModel()
                FinanceApp(vm)
            }
        }
    }
}
