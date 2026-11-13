
import React, { useState } from "react";
import "./AuthPage.css";

const API = "http://localhost:8082/api";

function AuthPage({ onLogin }) {
  const [page, setPage] = useState("member-login");
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    password: ""
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function updateForm(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function submit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const isRegister = page === "register";
      const isAdmin = page === "admin-login";

      const response = await fetch(
        `${API}/auth/${isRegister ? "register" : "login"}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(
            isRegister
              ? {
                  name: form.name,
                  email: form.email,
                  phone: form.phone,
                  password: form.password
                }
              : {
                  email: form.email,
                  password: form.password
                }
          )
        }
      );

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(
          data.message || data.error || "Unable to complete your request."
        );
      }

      if (isRegister && data.role !== "MEMBER") {
        throw new Error("Member registration did not return a member account.");
      }

      if (!isRegister && data.role !== (isAdmin ? "ADMIN" : "MEMBER")) {
        throw new Error(
          `This is not a ${isAdmin ? "admin" : "member"} account. Please use the correct login page.`
        );
      }

      onLogin(data);
    } catch (err) {
      setError(err.message || "Something went wrong.");
    } finally {
      setLoading(false);
    }
  }

  const isRegister = page === "register";
  const isAdmin = page === "admin-login";

  return (
    <div className="auth-page">
      <section className="auth-card">
        <div className="auth-logo">📚</div>
        <h1>Libra Hub</h1>
        <p className="auth-subtitle">Library Management System</p>

        <div className="auth-tabs">
          <button
            type="button"
            className={page === "admin-login" ? "selected" : ""}
            onClick={() => { setPage("admin-login"); setError(""); }}
          >
            Admin
          </button>
          <button
            type="button"
            className={page === "member-login" ? "selected" : ""}
            onClick={() => { setPage("member-login"); setError(""); }}
          >
            Member
          </button>
        </div>

        <h2>
          {isRegister
            ? "Create Member Account"
            : isAdmin
              ? "Admin Login"
              : "Member Login"}
        </h2>
        <p className="auth-description">
          {isRegister
            ? "Register to access your personal library dashboard."
            : isAdmin
              ? "Sign in to manage the library."
              : "Sign in to view your books and borrowing history."}
        </p>

        <form onSubmit={submit} className="auth-form">
          {isRegister && (
            <>
              <label>
                Full name
                <input
                  name="name"
                  value={form.name}
                  onChange={updateForm}
                  required
                  maxLength={100}
                />
              </label>
              <label>
                Phone number
                <input
                  name="phone"
                  type="tel"
                  value={form.phone}
                  onChange={updateForm}
                  required
                  minLength={10}
                  maxLength={15}
                />
              </label>
            </>
          )}

          <label>
            Email address
            <input
              name="email"
              type="email"
              value={form.email}
              onChange={updateForm}
              required
              maxLength={150}
            />
          </label>

          <label>
            Password
            <input
              name="password"
              type="password"
              value={form.password}
              onChange={updateForm}
              required
              minLength={isRegister ? 8 : undefined}
            />
          </label>

          {error && <div className="auth-error">{error}</div>}

          <button className="auth-submit" type="submit" disabled={loading}>
            {loading
              ? "Please wait..."
              : isRegister
                ? "Create Account"
                : "Sign In"}
          </button>
        </form>

        {!isAdmin && (
          <p className="auth-switch">
            {isRegister ? "Already registered?" : "New to Libra Hub?"}{" "}
            <button
              type="button"
              onClick={() => {
                setPage(isRegister ? "member-login" : "register");
                setError("");
              }}
            >
              {isRegister ? "Sign in" : "Create an account"}
            </button>
          </p>
        )}
      </section>
    </div>
  );
}

export default AuthPage;
