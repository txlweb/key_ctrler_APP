package com.idlike.kctrl.mgr

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View

class TemplateItemAnimator : DefaultItemAnimator() {
    
    private var addDuration: Long = 200
    private var removeDuration: Long = 200
    
    override fun getAddDuration(): Long = addDuration
    override fun getRemoveDuration(): Long = removeDuration
    
    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        view.alpha = 0f
        view.translationX = -50f
        
        val animator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f)
        val translationAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f)
        
        animator.duration = addDuration
        translationAnimator.duration = addDuration
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                dispatchAddFinished(holder)
            }
        })
        
        animator.start()
        translationAnimator.start()
        
        return false
    }
    
    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        
        val animator = ObjectAnimator.ofFloat(view, View.ALPHA, 0f)
        val translationAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -50f)
        
        animator.duration = removeDuration
        translationAnimator.duration = removeDuration
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.alpha = 1f
                view.translationX = 0f
                dispatchRemoveFinished(holder)
            }
        })
        
        animator.start()
        translationAnimator.start()
        
        return false
    }
    
    override fun setAddDuration(duration: Long) {
        this.addDuration = duration
    }
    
    override fun setRemoveDuration(duration: Long) {
        this.removeDuration = duration
    }
}