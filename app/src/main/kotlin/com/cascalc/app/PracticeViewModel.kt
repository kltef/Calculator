package com.cascalc.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cascalc.engine.CasEngine
import com.cascalc.engine.PracticeProblems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PracticeUiState(
    val topic: PracticeProblems.Topic = PracticeProblems.Topic.ARITHMETIC,
    val problem: PracticeProblems.Problem? = null,
    val answer: String = "",
    val mark: PracticeProblems.Mark? = null,
    val attempted: Int = 0,
    val correct: Int = 0,
)

class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val practice = PracticeProblems()

    /**
     * Marking needs an engine but must not contend with the calculator's, which
     * is busy with live previews; a private one keeps the two independent.
     */
    private val markingEngine by lazy { CasEngine() }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    init {
        next()
    }

    fun selectTopic(topic: PracticeProblems.Topic) {
        _uiState.value = _uiState.value.copy(topic = topic)
        next()
    }

    fun onAnswerChanged(answer: String) {
        _uiState.value = _uiState.value.copy(answer = answer, mark = null)
    }

    fun submit() {
        val state = _uiState.value
        val problem = state.problem ?: return
        viewModelScope.launch {
            val mark = withContext(Dispatchers.Default) {
                practice.mark(problem, state.answer, markingEngine)
            }
            // An unreadable answer is not a wrong answer, so it does not count
            // against the score - the student gets to try again.
            val counted = mark != PracticeProblems.Mark.Unreadable
            _uiState.value = _uiState.value.copy(
                mark = mark,
                attempted = state.attempted + if (counted) 1 else 0,
                correct = state.correct + if (mark == PracticeProblems.Mark.Correct) 1 else 0,
            )
        }
    }

    fun next() {
        val state = _uiState.value
        _uiState.value = state.copy(
            problem = practice.generate(state.topic),
            answer = "",
            mark = null,
        )
    }
}
