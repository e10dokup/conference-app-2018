package io.github.droidkaigi.confsched2018.presentation.detail

import android.annotation.TargetApi
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewCompat
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.droidkaigi.confsched2018.R
import io.github.droidkaigi.confsched2018.databinding.FragmentSessionDetailBinding
import io.github.droidkaigi.confsched2018.di.Injectable
import io.github.droidkaigi.confsched2018.model.Level
import io.github.droidkaigi.confsched2018.model.Session
import io.github.droidkaigi.confsched2018.presentation.NavigationController
import io.github.droidkaigi.confsched2018.presentation.Result
import io.github.droidkaigi.confsched2018.util.SessionAlarm
import io.github.droidkaigi.confsched2018.util.ext.addOnetimeOnPreDrawListener
import io.github.droidkaigi.confsched2018.util.ext.context
import io.github.droidkaigi.confsched2018.util.ext.drawable
import io.github.droidkaigi.confsched2018.util.ext.observe
import io.github.droidkaigi.confsched2018.util.lang
import timber.log.Timber
import javax.inject.Inject

class SessionDetailFragment : Fragment(), Injectable {
    private lateinit var binding: FragmentSessionDetailBinding
    @Inject lateinit var navigationController: NavigationController

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var sessionAlarm: SessionAlarm

    private val sessionDetailViewModel: SessionDetailViewModel by lazy {
        ViewModelProviders.of(activity!!, viewModelFactory).get(SessionDetailViewModel::class.java)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSessionDetailBinding.inflate(inflater, container!!, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionId = arguments!!.getString(EXTRA_SESSION_ID)
        sessionDetailViewModel.sessions.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    val sessions = result.data
                    val position = sessions.indexOfFirst { it.id == sessionId }
                    bindSession(sessions[position])
                    setSessionIndicator(sessions.getOrNull(position - 1),
                            sessions.getOrNull(position + 1))
                }
                is Result.Failure -> {
                    Timber.e(result.e)
                }
            }
        }

        binding.detailSessionsPrevSession.setOnClickListener {
            (activity as? OnClickBottomAreaListener)?.onClickPrevSession()
        }
        binding.detailSessionsNextSession.setOnClickListener {
            (activity as? OnClickBottomAreaListener)?.onClickNextSession()
        }

        binding.appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val factor = (-verticalOffset).toFloat() / appBarLayout.totalScrollRange.toFloat()
            binding.toolbarTextColorFactor = factor
        }

        binding.toolbar.setNavigationOnClickListener { activity?.finish() }

        val firstSessionId = (activity as? SessionDetailActivity)?.firstSessionId ?: return
        val transitionName = arguments!!.getString(EXTRA_TRANSITION_NAME)

        if (!TextUtils.isEmpty(transitionName)
                && firstSessionId == transitionName
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            initViewTransitions(view)
        }
    }

    private fun bindSession(session: Session.SpeechSession) {
        binding.session = session
        binding.fab.setOnClickListener {
            updateDrawable()
            sessionDetailViewModel.onFavoriteClick(session)
            sessionAlarm.toggleRegister(session)
        }
        binding.sessionTopic.text = session.topic.getNameByLang(lang())
        val levelDrawable = binding.context.drawable(when (session.level) {
            is Level.Beginner -> R.drawable.ic_beginner_lightgreen_20dp
            is Level.IntermediateOrExpert -> R.drawable.ic_intermediate_senior_bluegray_20dp
            is Level.Niche -> R.drawable.ic_niche_cyan_20dp
        })
        binding.level.setCompoundDrawablesRelativeWithIntrinsicBounds(
                levelDrawable, null, null, null)

        binding.goToFeedback.setOnClickListener {
            navigationController.navigateToSessionsFeedbackActivity(session)
        }
    }

    private fun updateDrawable() {
        val img = if (binding.fab.isActivated) R.drawable.ic_anim_favorite_unchecking else R.drawable.ic_anim_favorite_checking
        binding.fab.setImageResource(img)
        (binding.fab.drawable as? Animatable)?.start()
    }

    private fun setSessionIndicator(prevSession: Session.SpeechSession?,
                                    nextSession: Session.SpeechSession?) {
        binding.prevSession = prevSession
        binding.nextSession = nextSession
    }

    fun hideButton() {
        binding.fab.visibility = View.INVISIBLE
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initViewTransitions(view: View) {
        binding.speakerSummary.addOnetimeOnPreDrawListener {
            ViewCompat.setTransitionName(
                    view.findViewById<View>(R.id.speaker_summary),
                    arguments!!.getString(EXTRA_TRANSITION_NAME))
            activity?.supportStartPostponedEnterTransition()
        }
    }

    interface OnClickBottomAreaListener {
        fun onClickPrevSession()
        fun onClickNextSession()
    }

    companion object {
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_TRANSITION_NAME = "EXTRA_TRANSITION_NAME"

        fun newInstance(sessionId: String): SessionDetailFragment = SessionDetailFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_SESSION_ID, sessionId)
            }
        }

        fun newInstance(sessionId: String, transitionName: String): SessionDetailFragment =
                SessionDetailFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_SESSION_ID, sessionId)
                putString(EXTRA_TRANSITION_NAME, transitionName)
            }
        }
    }
}
