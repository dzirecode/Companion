package com.dzire.eduseva360.core.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dzire.eduseva360.features.auth.AuthViewModel
import com.dzire.eduseva360.features.auth.LoginScreen
import com.dzire.eduseva360.features.dashboard.DashboardScreen
import com.dzire.eduseva360.features.students.StudentManagementScreen
import com.dzire.eduseva360.features.users.UserManagementScreen
import com.dzire.eduseva360.features.attendance.AttendanceScreen
import com.dzire.eduseva360.features.parents.ParentLinkedStudentsScreen
import com.dzire.eduseva360.features.hostel.WardenHostelScreen
import com.dzire.eduseva360.features.reports.AttendanceReportsScreen
import com.dzire.eduseva360.features.announcements.AnnouncementsScreen
import com.dzire.eduseva360.features.academics.AcademicRecordsScreen

@Composable
fun EduSevaNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(viewModel = authViewModel, onLoggedIn = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = authViewModel,
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
                onStudentsClick = { navController.navigate(Routes.STUDENTS) },
                onUsersClick = { navController.navigate(Routes.USERS) },
                onAttendanceClick = { navController.navigate(Routes.ATTENDANCE) },
                onParentPortalClick = { navController.navigate(Routes.PARENT_PORTAL) },
                onHostelClick = { navController.navigate(Routes.HOSTEL) },
                onAttendanceReportsClick = { navController.navigate(Routes.ATTENDANCE_REPORTS) },
                onAnnouncementsClick = { navController.navigate(Routes.ANNOUNCEMENTS) },
                onAcademicsClick = { navController.navigate(Routes.ACADEMICS) }
            )
        }
        composable(Routes.STUDENTS) {
            StudentManagementScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.USERS) {
            UserManagementScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ATTENDANCE) {
            AttendanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PARENT_PORTAL) { ParentLinkedStudentsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HOSTEL) { WardenHostelScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ATTENDANCE_REPORTS) { AttendanceReportsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ANNOUNCEMENTS) { AnnouncementsScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel) }
        composable(Routes.ACADEMICS) { AcademicRecordsScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel) }
    }
}
