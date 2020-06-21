package com.absinthe.libchecker.ui.detail

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.absinthe.libchecker.BaseActivity
import com.absinthe.libchecker.R
import com.absinthe.libchecker.database.LCDatabase
import com.absinthe.libchecker.database.LCRepository
import com.absinthe.libchecker.databinding.ActivityAppDetailBinding
import com.absinthe.libchecker.databinding.LayoutChipGroupBinding
import com.absinthe.libchecker.ui.fragment.applist.ComponentsAnalysisFragment
import com.absinthe.libchecker.ui.fragment.applist.SoAnalysisFragment
import com.absinthe.libchecker.utils.PackageUtils
import com.absinthe.libchecker.view.EXTRA_PKG_NAME
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.IntentUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityAppDetailBinding
    private val pkgName by lazy { intent.getStringExtra(EXTRA_PKG_NAME) }

    init {
        isPaddingToolbar = true
    }

    override fun setViewBinding(): View {
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initTransition()
        super.onCreate(savedInstanceState)
        initView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            supportFinishAfterTransition()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        supportFinishAfterTransition()
    }

    private fun initTransition() {
        window.apply {
            requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            sharedElementEnterTransition = MaterialContainerTransform().apply {
                addTarget(android.R.id.content)
                duration = 300L
            }
            sharedElementReturnTransition = MaterialContainerTransform().apply {
                addTarget(android.R.id.content)
                duration = 250L
            }
        }
        findViewById<View>(android.R.id.content).transitionName = "app_card_container"
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    }

    private fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        pkgName?.let { packageName ->
            supportActionBar?.apply {
                title = AppUtils.getAppName(packageName)
            }
            binding.apply {
                val packageInfo = PackageUtils.getPackageInfo(packageName)
                ivAppIcon.apply {
                    setImageDrawable(AppUtils.getAppIcon(packageName))
                    setOnClickListener {
                        try {
                            startActivity(IntentUtils.getLaunchAppIntent(pkgName))
                        } catch (e: ActivityNotFoundException) {
                            ToastUtils.showShort("Can\'t open this app")
                        } catch (e: NullPointerException) {
                            ToastUtils.showShort("Package name is null")
                        }
                    }
                }
                tvAppName.text = AppUtils.getAppName(packageName)
                tvPackageName.text = packageName
                tvVersion.text = PackageUtils.getVersionString(packageInfo)
                tvTargetApi.text = PackageUtils.getTargetApiString(packageInfo)

                lifecycleScope.launch(Dispatchers.IO) {
                    val lcDao = LCDatabase.getDatabase(application).lcDao()
                    val repository = LCRepository(lcDao)
                    val lcItem = repository.getItem(packageName)
                    val chipGroupBinding =
                        LayoutChipGroupBinding.inflate(layoutInflater).apply {
                            chipSplitApk.isVisible = lcItem?.isSplitApk ?: false
                            chipKotlinUsed.isVisible = lcItem?.isKotlinUsed ?: false
                        }
                    chipGroupBinding.root.id = View.generateViewId()

                    withContext(Dispatchers.Main) {
                        binding.headerContentLayout.addView(chipGroupBinding.root)
                        ConstraintSet().apply {
                            clone(binding.headerContentLayout)
                            connect(
                                chipGroupBinding.root.id,
                                ConstraintSet.TOP,
                                binding.ivAppIcon.id,
                                ConstraintSet.BOTTOM,
                                resources.getDimension(R.dimen.normal_padding).toInt()
                            )
                            applyTo(binding.headerContentLayout)
                        }
                    }
                }
            }
        } ?: supportFinishAfterTransition()

        binding.viewpager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int {
                return 2
            }

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> SoAnalysisFragment.newInstance(pkgName!!)
                    else -> ComponentsAnalysisFragment.newInstance(pkgName!!)
                }
            }
        }

        val mediator = TabLayoutMediator(binding.tabLayout, binding.viewpager,
            TabLayoutMediator.TabConfigurationStrategy { tab, position ->
                when (position) {
                    0 -> tab.text = getText(R.string.tab_so_analysis)
                    else -> tab.text = getText(R.string.tab_components_analysis)
                }
            })
        mediator.attach()
    }
}