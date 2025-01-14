package apply.application

import apply.BIRTHDAY
import apply.CONFIRM_PASSWORD
import apply.EMAIL
import apply.GENDER
import apply.INVALID_CODE
import apply.NAME
import apply.PASSWORD
import apply.PHONE_NUMBER
import apply.VALID_CODE
import apply.VALID_TOKEN
import apply.WRONG_PASSWORD
import apply.createAuthenticationCode
import apply.createUser
import apply.domain.authenticationcode.AuthenticationCodeRepository
import apply.domain.authenticationcode.getLastByEmail
import apply.domain.user.UnidentifiedUserException
import apply.domain.user.UserRepository
import apply.domain.user.existsByEmail
import apply.domain.user.findByEmail
import apply.security.JwtTokenProvider
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import support.test.UnitTest

@UnitTest
internal class UserAuthenticationServiceTest {
    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var authenticationCodeRepository: AuthenticationCodeRepository

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var userAuthenticationService: UserAuthenticationService

    @BeforeEach
    internal fun setUp() {
        every { jwtTokenProvider.createToken(any()) } returns VALID_TOKEN
        userAuthenticationService = UserAuthenticationService(
            userRepository, authenticationCodeRepository, jwtTokenProvider
        )
    }

    @DisplayName("(회원 가입) 토큰 생성은")
    @Nested
    inner class GenerateTokenByRegister {
        private lateinit var request: RegisterUserRequest

        fun subject(): String {
            return userAuthenticationService.generateTokenByRegister(request)
        }

        @Test
        fun `가입된 이메일이라면 예외가 발생한다`() {
            every { userRepository.existsByEmail(any()) } returns true
            request = RegisterUserRequest(
                NAME, EMAIL, PHONE_NUMBER, GENDER, BIRTHDAY, PASSWORD, CONFIRM_PASSWORD, VALID_CODE
            )
            assertThrows<IllegalStateException> { subject() }
        }

        @Test
        fun `인증된 인증 코드와 일치하지 않는다면 예외가 발생한다`() {
            every { userRepository.existsByEmail(any()) } returns false
            every { authenticationCodeRepository.getLastByEmail(any()) } returns
                createAuthenticationCode(EMAIL, INVALID_CODE)
            request = RegisterUserRequest(
                NAME, EMAIL, PHONE_NUMBER, GENDER, BIRTHDAY, PASSWORD, CONFIRM_PASSWORD, VALID_CODE
            )
            assertThrows<IllegalStateException> { subject() }
        }

        @Test
        fun `인증된 이메일이 아니라면 예외가 발생한다`() {
            every { userRepository.existsByEmail(any()) } returns false
            every { authenticationCodeRepository.getLastByEmail(any()) } returns
                createAuthenticationCode(EMAIL, INVALID_CODE)
            request = RegisterUserRequest(
                NAME, "not@email.com", PHONE_NUMBER, GENDER, BIRTHDAY, PASSWORD, CONFIRM_PASSWORD, VALID_CODE
            )
            assertThrows<IllegalStateException> { subject() }
        }

        @Test
        fun `가입되지 않고 인증된 이메일이라면 회원을 저장하고 토큰을 반환한다`() {
            every { userRepository.existsByEmail(any()) } returns false
            every { authenticationCodeRepository.getLastByEmail(any()) } returns
                createAuthenticationCode(EMAIL, VALID_CODE, true)

            every { userRepository.save(any()) } returns createUser()
            request = RegisterUserRequest(
                NAME, EMAIL, PHONE_NUMBER, GENDER, BIRTHDAY, PASSWORD, CONFIRM_PASSWORD, VALID_CODE
            )
            assertThat(subject()).isEqualTo(VALID_TOKEN)
        }

        @Test
        fun `확인용 비밀번호가 일치하지 않으면 예외가 발생한다`() {
            request = RegisterUserRequest(
                NAME, EMAIL, PHONE_NUMBER, GENDER, BIRTHDAY, PASSWORD, WRONG_PASSWORD, VALID_CODE
            )
            assertThrows<IllegalArgumentException> { subject() }
        }
    }

    @DisplayName("(로그인) 토큰 생성은")
    @Nested
    inner class GenerateTokenByLogin {
        private lateinit var request: AuthenticateUserRequest

        fun subject(): String {
            return userAuthenticationService.generateTokenByLogin(request)
        }

        @Test
        fun `회원이 존재하고 인증에 성공하면 유효한 토큰을 반환한다`() {
            every { userRepository.findByEmail(any()) } returns createUser()
            request = AuthenticateUserRequest(EMAIL, PASSWORD)
            assertThat(subject()).isEqualTo(VALID_TOKEN)
        }

        @Test
        fun `회원이 존재하지만 인증에 실패하면 예외가 발생한다`() {
            every { userRepository.findByEmail(any()) } returns createUser()
            request = AuthenticateUserRequest(EMAIL, WRONG_PASSWORD)
            assertThrows<UnidentifiedUserException> { subject() }
        }

        @Test
        fun `회원이 존재하지 않다면 예외가 발생한다`() {
            every { userRepository.findByEmail(any()) } returns null
            request = AuthenticateUserRequest(EMAIL, PASSWORD)
            assertThrows<UnidentifiedUserException> { subject() }
        }
    }

    @DisplayName("이메일 사용자 인증 시")
    @Nested
    inner class AuthenticateEmail {
        private val authenticationCode = createAuthenticationCode()

        @Test
        fun `인증 코드가 일치한다면 인증된 회원으로 변경한다`() {
            every { authenticationCodeRepository.getLastByEmail(any()) } returns authenticationCode
            userAuthenticationService.authenticateEmail(authenticationCode.email, authenticationCode.code)
            assertThat(authenticationCode.authenticated).isTrue()
        }

        @Test
        fun `인증 코드가 일치하지 않는다면 예외가 발생한다`() {
            every { authenticationCodeRepository.getLastByEmail(any()) } returns authenticationCode
            assertThrows<IllegalArgumentException> {
                userAuthenticationService.authenticateEmail(authenticationCode.email, INVALID_CODE)
            }
        }

        @Test
        fun `인증 코드를 생성한다`() {
            every { userRepository.existsByEmail(any()) } returns false
            every { authenticationCodeRepository.save(any()) } returns authenticationCode
            val actual = userAuthenticationService.generateAuthenticationCode(authenticationCode.email)
            assertThat(actual).isEqualTo(authenticationCode.code)
        }

        @Test
        fun `인증 코드 요청시 이미 가입된 이메일이라면 예외가 발생한다`() {
            every { userRepository.existsByEmail(any()) } returns true
            assertThrows<IllegalStateException> {
                userAuthenticationService.generateAuthenticationCode(authenticationCode.email)
            }
        }
    }
}
