package demo.designsystem

fun readOnlySheet() {
    AppBottomSheet(onDismissRequest = {}, dismissOnClickOutside = true) {}
}

fun passThrough(dismissOnClickOutside: Boolean) {
    ModalBottomSheetProperties(shouldDismissOnBackPress = true, shouldDismissOnClickOutside = dismissOnClickOutside)
}
