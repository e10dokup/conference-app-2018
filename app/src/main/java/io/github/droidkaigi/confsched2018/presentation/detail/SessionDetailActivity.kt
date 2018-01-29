package io.github.droidkaigi.confsched2018.presentation.detail

import android.app.Activity
import android.app.SharedElementCallback
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.util.Pair
import android.support.v4.view.ViewCompat
import android.support.v4.view.ViewPager
import android.view.View
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import io.github.droidkaigi.confsched2018.R
import io.github.droidkaigi.confsched2018.databinding.ActivitySessionDetailBinding
import io.github.droidkaigi.confsched2018.model.Session
import io.github.droidkaigi.confsched2018.presentation.NavigationController
import io.github.droidkaigi.confsched2018.presentation.Result
import io.github.droidkaigi.confsched2018.presentation.common.activity.BaseActivity
import io.github.droidkaigi.confsched2018.presentation.common.menu.DrawerMenu
import io.github.droidkaigi.confsched2018.presentation.common.view.SpeakersSummaryLayout
import io.github.droidkaigi.confsched2018.util.ext.observe
import timber.log.Timber
import javax.inject.Inject

class SessionDetailActivity :
        BaseActivity(),
        HasSupportFragmentInjector,
        SessionDetailFragment.OnClickBottomAreaListener,
        SessionDetailFragment.OnSetTransitionNameListener {
    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>
    @Inject lateinit var navigationController: NavigationController
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var drawerMenu: DrawerMenu

    private var backPressed = false

    private val binding: ActivitySessionDetailBinding by lazy {
        DataBindingUtil
                .setContentView<ActivitySessionDetailBinding>(
                        this,
                        R.layout.activity_session_detail
                )
    }

    private val sessionDetailViewModel: SessionDetailViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(SessionDetailViewModel::class.java)
    }

    private val pagerAdapter = SessionDetailFragmentPagerAdapter(supportFragmentManager)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(false)
        }
        sessionDetailViewModel.sessions.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    val sessions = result.data
                    bindSessions(sessions)
                }
                is Result.Failure -> {
                    Timber.e(result.e)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setEnterSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>?,
                                                 sharedElements: MutableMap<String, View>?) {
                    super.onMapSharedElements(names, sharedElements)

                    if (backPressed) {
                        val currentFragment = pagerAdapter.findFragmentByPosition(
                                binding.detailSessionsPager, binding.detailSessionsPager.currentItem
                        )
                        sharedElements?.clear()
                        sharedElements?.put(SessionDetailActivity.SPEAKERS_SUMMARY_TRANSITION,
                                currentFragment.speakersSummary)
                    }

                }
            })
            postponeEnterTransition()
        }

        binding.detailSessionsPager.adapter = pagerAdapter
        drawerMenu.setup(binding.drawerLayout, binding.drawer)
    }

    private fun bindSessions(sessions: List<Session.SpeechSession>) {
        val firstAssign = pagerAdapter.sessions.isEmpty() && sessions.isNotEmpty()
        pagerAdapter.sessions = sessions
        if (firstAssign) {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            val position = sessions.indexOfFirst { it.id == sessionId }
            binding
                    .detailSessionsPager
                    .setCurrentItem(
                            position,
                            false
                    )
        }
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment> = dispatchingAndroidInjector

    override fun onBackPressed() {
        backPressed = true

        if (drawerMenu.closeDrawerIfNeeded()) {
            super.onBackPressed()
        }
    }

    override fun onClickPrevSession() {
        binding.detailSessionsPager.currentItem = binding.detailSessionsPager.currentItem - 1
    }

    override fun onClickNextSession() {
        binding.detailSessionsPager.currentItem = binding.detailSessionsPager.currentItem + 1
    }

    override fun onSetTransitionName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startPostponedEnterTransition()
        }
    }

    class SessionDetailFragmentPagerAdapter(
            fragmentManager: FragmentManager
    ) : FragmentStatePagerAdapter(fragmentManager) {
        var sessions: List<Session.SpeechSession> = listOf()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getItem(position: Int): Fragment {
            return SessionDetailFragment.newInstance(sessions[position].id)
        }

        override fun getCount(): Int = sessions.size

        fun findFragmentByPosition(viewPager: ViewPager, position: Int): SessionDetailFragment {
            return instantiateItem(viewPager, position) as SessionDetailFragment
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val SPEAKERS_SUMMARY_TRANSITION = "SPEAKERS_SUMMARY"

        fun start(context: Context, session: Session) {
            context.startActivity(createIntent(context, session.id))
        }

        fun startWithAnimation(activity: Activity, session: Session,
                               speakersSummary: SpeakersSummaryLayout?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && speakersSummary == null) {
                start(activity, session)
                return
            }

            ViewCompat.setTransitionName(speakersSummary, SPEAKERS_SUMMARY_TRANSITION)
            val targetPair = Pair<View, String>(speakersSummary, SPEAKERS_SUMMARY_TRANSITION)
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, targetPair)

            activity.startActivity(createIntent(activity, session.id), options.toBundle())


        }

        fun createIntent(context: Context, sessionId: String): Intent {
            return Intent(context, SessionDetailActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }
    }
}
