# بقالة العزي للمواد الغذائية

مشروع Android Native مستقل لإدارة البقالة.

## هوية المشروع
- الاسم: بقالة العزي للمواد الغذائية (Al Azzi Grocery Management)
- Android applicationId: `com.saleh.alazzi.grocery`
- المستودع: `saleh12340/The-latest-grocery-store-management-app`
- الفرع المستهدف: `main`

## التقنية المستهدفة
Kotlin, Jetpack Compose, Material 3, MVVM, Room/SQLite, Coroutines, Flow, Navigation Compose.

## حالة التنفيذ
هذه نقطة تأسيس أولية فقط. لا يُعد وجود هذا الملف دليلاً على اكتمال التطبيق أو نجاح البناء. يجب إضافة ملفات Android الفعلية والاختبارات وWorkflow، ثم تنفيذ Gradle والتحقق من APK في بيئة مناسبة.

## قواعد سلامة المشروع
- هذا المشروع مستقل عن أي تطبيق سابق.
- لا تستخدم WebView أو LocalStorage.
- جميع العمليات المالية والمخزنية يجب أن تحفظ ذرياً داخل Room transactions.
- لا تستخدم destructive migrations.
- لا تدّعِ نجاح اختبار أو بناء لم يُنفذ فعلياً.
