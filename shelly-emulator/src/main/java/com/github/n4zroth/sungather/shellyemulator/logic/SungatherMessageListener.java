package com.github.n4zroth.sungather.shellyemulator.logic;

import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;

public interface SungatherMessageListener {
    void handleMessage(SungatherMessage sungatherMessage);
}
