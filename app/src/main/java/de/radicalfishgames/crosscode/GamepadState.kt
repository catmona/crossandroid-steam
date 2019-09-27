package de.radicalfishgames.crosscode


data class GamepadState(
    val leftStick: Stick = Stick(),
    val rightStick: Stick = Stick(),

    val leftCross: ButtonCross = ButtonCross(),
    val rightCross: ButtonCross = ButtonCross(),

    val leftBumper: Button = Button(),
    val rightBumper: Button = Button(),

    val leftTrigger: Button = Button(),
    val rightTrigger: Button = Button(),

    val selectOrBack: Button = Button(),
    val startOrForward: Button = Button(),
    val center: Button = Button()
){

    data class ButtonCross(
        val top: Button = Button(),
        val bottom: Button = Button(),
        val left: Button = Button(),
        val right: Button = Button()
    )

    data class Stick(
        // negative = left; positive = right
        var horizontal: Double = AXE_NOT_MOVED,
        // negative = up; positive = down
        var vertical: Double = AXE_NOT_MOVED,
        val button: Button = Button()
    )

    data class Button(
        var pressed: Boolean = false
    ){
        val value: Double
            get() {
                if(pressed){
                    return 1.0
                }else{
                    return 0.0
                }
            }
    }

    companion object {
        const val AXE_NOT_MOVED = 0.0
    }
}
