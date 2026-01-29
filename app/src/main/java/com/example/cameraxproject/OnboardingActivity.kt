    package com.example.cameraxproject

    import android.content.Intent
    import android.os.Bundle
    import android.view.View
    import android.view.WindowManager
    import android.widget.Button
    import android.widget.TextView
    import androidx.appcompat.app.AppCompatActivity
    import androidx.viewpager2.widget.ViewPager2
    import com.tbuonomo.viewpagerdotsindicator.DotsIndicator

    class OnboardingActivity : AppCompatActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // FULL SCREEN
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            setContentView(R.layout.activity_onboarding)

            val viewPager = findViewById<ViewPager2>(R.id.viewPager)
            val dots = findViewById<DotsIndicator>(R.id.dotsIndicator)
            val btnSkip = findViewById<TextView>(R.id.btnSkip)
            val btnDone = findViewById<Button>(R.id.btnDone)

            val images = listOf(
                R.drawable.hand1,
                R.drawable.hand2,
                R.drawable.hand3
            )

            val titles = listOf(
                "Keep Hand in Frame",
                "Hold Hand Steady ",
                "Maintain Distance"
            )

            val descs = listOf(
                "Ensure hand remains fully visible",
                "Ensure image is focused and clear",
                "Maintain a moderate distance from the lens"

            )

            val adapter = OnboardingAdapter(images, titles, descs)
            viewPager.adapter = adapter

            dots.attachTo(viewPager)

            btnSkip.setOnClickListener {
                finishOnboarding()
            }

            btnDone.setOnClickListener {
                finishOnboarding()
            }

            viewPager.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(position: Int) {
                    if (position == images.size - 1) {
                        btnDone.visibility = View.VISIBLE
                        btnSkip.visibility = View.GONE
                    } else {
                        btnDone.visibility = View.GONE
                        btnSkip.visibility = View.VISIBLE
                    }
                }
            })
        }

        private fun finishOnboarding() {
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("first_time", false)
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
