package com.example.tdbehaviortestdemo_android

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView

class TicketRushActivity : BaseAuthActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var processingDialog: AlertDialog? = null
    private var paymentDialog: AlertDialog? = null
    private var selectedAmount = "¥102.00"
    private lateinit var submitOrderButton: Button
    private var releaseSecondsRemaining = TICKET_RELEASE_COUNTDOWN_SECONDS

    private val updateTicketReleaseCountdown = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (releaseSecondsRemaining > 0) {
                submitOrderButton.isEnabled = false
                submitOrderButton.text = getString(
                    R.string.ticket_release_countdown,
                    releaseSecondsRemaining
                )
                releaseSecondsRemaining--
                mainHandler.postDelayed(this, COUNTDOWN_TICK_MS)
            } else {
                submitOrderButton.text = getString(R.string.submit_order)
                submitOrderButton.isEnabled = true
            }
        }
    }

    private val finishOrderProcessing = Runnable {
        processingDialog?.dismiss()
        processingDialog = null
        if (!isFinishing && !isDestroyed) showPaymentDialog()
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.ticket_blue)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_ticket_rush)
        preventInitialInputFocus()

        findViewById<Button>(R.id.ticket_back_button).setOnClickListener { finish() }

        val seatGroup = findViewById<RadioGroup>(R.id.seat_group)
        seatGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedAmount = when (checkedId) {
                R.id.first_class_option -> "¥178.00"
                else -> "¥102.00"
            }
        }

        submitOrderButton = findViewById(R.id.submit_order_button)
        submitOrderButton.setOnClickListener {
            showOrderProcessing()
        }
        mainHandler.removeCallbacks(updateTicketReleaseCountdown)
        releaseSecondsRemaining = TICKET_RELEASE_COUNTDOWN_SECONDS
        updateTicketReleaseCountdown.run()
    }

    private fun showOrderProcessing() {
        if (processingDialog?.isShowing == true || paymentDialog?.isShowing == true) return

        val content = layoutInflater.inflate(R.layout.dialog_order_processing, null)
        processingDialog = AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .create()
            .also { it.show() }

        mainHandler.removeCallbacks(finishOrderProcessing)
        mainHandler.postDelayed(finishOrderProcessing, ORDER_PROCESSING_DELAY_MS)
    }

    private fun showPaymentDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_payment, null)
        content.findViewById<TextView>(R.id.payment_amount).text = selectedAmount
        val payButton = content.findViewById<Button>(R.id.pay_button)

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .create()
        paymentDialog = dialog
        dialog.setOnDismissListener {
            if (paymentDialog === dialog) paymentDialog = null
        }
        payButton.setOnClickListener {
            dialog.dismiss()
            submitRiskCheck(payButton, getString(R.string.payment_risk_result_title))
        }
        dialog.show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(updateTicketReleaseCountdown)
        mainHandler.removeCallbacks(finishOrderProcessing)
        processingDialog?.dismiss()
        processingDialog = null
        paymentDialog?.dismiss()
        paymentDialog = null
        super.onDestroy()
    }

    private companion object {
        const val TICKET_RELEASE_COUNTDOWN_SECONDS = 10
        const val COUNTDOWN_TICK_MS = 1_000L
        const val ORDER_PROCESSING_DELAY_MS = 1_500L
    }
}
