
import React, { useEffect, useState } from "react";
import "./App.css";
import AuthPage from "./AuthPage";

const API = "http://localhost:8082/api";

function getSavedUser() {
  try {
    return JSON.parse(localStorage.getItem("librahubUser") || "null");
  } catch {
    return null;
  }
}

async function apiFetch(path, options = {}) {
  const user = getSavedUser();

  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
      ...(user?.token ? { Authorization: `Bearer ${user.token}` } : {}),
    },
  });

  if (response.status === 401 || response.status === 403) {
    throw new Error(
      response.status === 401
        ? "Your session may have expired. Please log in again."
        : "You do not have permission to perform this action."
    );
  }

  if (!response.ok) {
    const text = await response.text();
    let message = text;
    try {
      const data = JSON.parse(text);
      message = data.message || data.error || text;
    } catch {}
    throw new Error(message || `Request failed: ${response.status}`);
  }

  if (response.status === 204) return null;

  return response.json().catch(() => null);
}

function App() {
  const [user, setUser] = useState(getSavedUser);
  const [page, setPage] = useState("Dashboard");
  const [stats, setStats] = useState({});
  const [books, setBooks] = useState([]);
  const [members, setMembers] = useState([]);
  const [loans, setLoans] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const [memberForm, setMemberForm] = useState({
    name: "", email: "", phone: ""
  });

  const [bookForm, setBookForm] = useState({
    title: "", author: "", category: "", isbn: "", totalCopies: "1"
  });

  const [issueForm, setIssueForm] = useState({
    bookId: "", memberId: ""
  });

  const isAdmin = user?.role === "ADMIN";

  function handleLogin(data) {
    localStorage.setItem("librahubUser", JSON.stringify(data));
    setUser(data);
    setPage("Dashboard");
    setError("");
    setMessage("");
  }

  function handleLogout() {
    localStorage.removeItem("librahubUser");
    setUser(null);
    setStats({});
    setBooks([]);
    setMembers([]);
    setLoans([]);
    setPage("Dashboard");
    setError("");
    setMessage("");
  }

  async function loadData(showLoading = true) {
    if (!user) return;

    if (showLoading) setLoading(true);
    setError("");

    try {
      if (isAdmin) {
        const [s, b, m, l] = await Promise.all([
          apiFetch("/dashboard/stats"),
          apiFetch("/books"),
          apiFetch("/members"),
          apiFetch("/borrow"),
        ]);

        setStats(s || {});
        setBooks(Array.isArray(b) ? b : []);
        setMembers(Array.isArray(m) ? m : []);
        setLoans(Array.isArray(l) ? l : []);
      } else {
        const [dashboard, publicBooks] = await Promise.all([
          apiFetch("/dashboard/member"),
          apiFetch("/books"),
        ]);

        setStats(dashboard || {});
        setBooks(Array.isArray(publicBooks) ? publicBooks : []);
        setMembers([]);
        setLoans(
          Array.isArray(dashboard?.borrowings)
            ? dashboard.borrowings
            : Array.isArray(dashboard?.records)
              ? dashboard.records
              : Array.isArray(dashboard?.loans)
                ? dashboard.loans
                : []
        );
      }
    } catch (e) {
      setError(e.message || "Could not load library data.");
      if (e.message?.includes("session may have expired")) {
        handleLogout();
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (user) loadData();
  }, [user]);

  async function sendRequest(path, method, body) {
    return apiFetch(path, {
      method,
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
  }

  async function performAction(action, successMessage) {
    setBusy(true);
    setMessage("");
    setError("");

    try {
      await action();
      setMessage(successMessage);
      await loadData(false);
    } catch (e) {
      setError(e.message || "Operation failed.");
    } finally {
      setBusy(false);
    }
  }

  async function addMember(e) {
    e.preventDefault();
    await performAction(async () => {
      await sendRequest("/members", "POST", memberForm);
      setMemberForm({ name: "", email: "", phone: "" });
    }, "Member added successfully.");
  }

  async function addBook(e) {
    e.preventDefault();
    await performAction(async () => {
      await sendRequest("/books", "POST", {
        ...bookForm,
        totalCopies: Number(bookForm.totalCopies),
      });
      setBookForm({
        title: "", author: "", category: "", isbn: "", totalCopies: "1"
      });
    }, "Book added successfully.");
  }

  async function issueBook(e) {
    e.preventDefault();
    await performAction(async () => {
      await sendRequest("/borrow/issue", "POST", {
        bookId: Number(issueForm.bookId),
        memberId: Number(issueForm.memberId),
      });
      setIssueForm({ bookId: "", memberId: "" });
    }, "Book issued successfully.");
  }

async function borrowBookForMember(bookId) {
  if (isAdmin) return;

  await performAction(
    () => sendRequest("/borrow/member/issue", "POST", { bookId }),
    "Book borrowed successfully! Check My Borrowing for the due date."
  );
}


  async function returnBook(id) {
    if (!isAdmin) return;
    if (!window.confirm("Return this book now?")) return;

    await performAction(
      () => sendRequest(`/borrow/return/${id}`, "POST"),
      "Book returned successfully."
    );
  }

  const totalCopies = books.reduce(
    (sum, book) => sum + Number(book.totalCopies || 0), 0
  );

  const availableCopies = books.reduce(
    (sum, book) => sum + Number(book.availableCopies || 0), 0
  );

  const issuedLoans = loans.filter(
    loan => loan.status === "ISSUED"
  ).length;

  const overdueLoans = loans.filter(
    loan => loan.status === "OVERDUE"
  );

  const returnedLoans = loans.filter(
    loan => loan.status === "RETURNED"
  ).length;

  const totalFine = overdueLoans.reduce(
    (sum, loan) => sum + Number(loan.fineAmount || 0), 0
  );

  const navItems = isAdmin
    ? [["Dashboard", "▦"], ["Books", "▤"], ["Members", "♙"],
       ["Borrowing", "↔"], ["Overdue", "⚠"]]
    : [["Dashboard", "▦"], ["Books", "▤"], ["My Borrowing", "↔"]];

  if (!user) {
    return <AuthPage onLogin={handleLogin} />;
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <h2>📚 Libra Hub</h2>
        <p className="subtitle">Library Management System</p>

        {navItems.map(([name, icon]) => (
          <button
            key={name}
            className={`nav-button ${page === name ? "active" : ""}`}
            onClick={() => {
              setPage(name);
              setMessage("");
              setError("");
            }}
          >
            <span>{icon}</span> {name}
            {name === "Overdue" && overdueLoans.length > 0 && (
              <span className="nav-count">{overdueLoans.length}</span>
            )}
          </button>
        ))}

        <button className="refresh-button" onClick={() => loadData()}>
          ↻ Refresh data
        </button>


<p className="sidebar-footer">LIBRA HUB · {user.role}</p>

        <button className="refresh-button" onClick={handleLogout}>
          Log out
        </button>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <p className="eyebrow">
              {isAdmin ? "ADMIN WORKSPACE" : "MEMBER WORKSPACE"}
            </p>
            <h1>{page}</h1>
            <p>
              {isAdmin
                ? "Manage the collection and track library activity."
                : "Explore books and check your personal borrowing history."}
            </p>
          </div>
          <span className="status">● Signed in as {user.role}</span>
        </header>

        {message && <div className="success-message">{message}</div>}
        {error && (
          <div className="error">
            {error} <button onClick={() => loadData()}>Retry</button>
          </div>
        )}
        {loading && <div className="panel">Loading library data...</div>}

        {!loading && !error && (
          <>
            {page === "Dashboard" && isAdmin && (
              <>
                <section className="stats">
                  <Stat title="Book Titles" value={books.length} icon="▤" />
                  <Stat title="Total Copies" value={totalCopies} icon="▣" />
                  <Stat title="Members" value={members.length} icon="♙" />
                  <Stat title="Books Available" value={availableCopies} icon="✓" />
                </section>

                <section className="stats stats-secondary">
                  <Stat title="Currently Issued" value={issuedLoans} icon="↗" />
                  <Stat title="Overdue Loans" value={overdueLoans.length} icon="!" />
                  <Stat title="Returned Loans" value={returnedLoans} icon="↩" />
                  <Stat title="Overdue Fines" value={`₹${totalFine.toFixed(2)}`} icon="₹" />
                </section>

                <div className="dashboard-grid">
                  <section className="panel">
                    <h2>Book Availability</h2>
                    <p className="panel-subtitle">Available versus unavailable copies</p>
                    <div className="chart-total">{totalCopies}</div>
                    <p className="chart-caption">Total book copies</p>
                    <Bar label="Available" value={availableCopies} total={totalCopies} color="available" />
                    <Bar label="Unavailable" value={Math.max(0, totalCopies - availableCopies)} total={totalCopies} color="unavailable" />
                  </section>

                  <section className="panel">
                    <h2>Borrowing Status</h2>
                    <p className="panel-subtitle">All borrowing records</p>
                    <p>Issued: <strong>{issuedLoans}</strong></p>
                    <p>Overdue: <strong>{overdueLoans.length}</strong></p>
                    <p>Returned: <strong>{returnedLoans}</strong></p>
                    <p className="chart-caption">{loans.length} total records</p>
                  </section>
                </div>

                <section className="panel">
                  <h2>Overdue Fine Alerts</h2>
                  {overdueLoans.length
                    ? <LoanTable loans={overdueLoans} onReturn={returnBook} busy={busy} />
                    : <p>No overdue books recorded.</p>}
                </section>

                <section className="panel">
                  <h2>Recently Added Books</h2>
                  <BookTable books={[...books].reverse().slice(0, 5)} />
                </section>
              </>
            )}

            {page === "Dashboard" && !isAdmin && (
              <>
                <section className="stats">
                  <Stat title="Book Titles in Library" value={books.length} icon="▤" />
                  <Stat title="Available Copies" value={availableCopies} icon="✓" />
                  <Stat title="My Borrowings" value={loans.length} icon="↔" />
                  <Stat title="Currently Issued" value={issuedLoans} icon="↗" />
                </section>

                <div className="dashboard-grid">
                  <section className="panel">
                    <h2>My Borrowing Status</h2>
                    <p className="panel-subtitle">Based on your personal records</p>
                    <Bar label="Issued" value={issuedLoans} total={loans.length} color="available" />
                    <Bar label="Overdue" value={overdueLoans.length} total={loans.length} color="unavailable" />
                    <Bar label="Returned" value={returnedLoans} total={loans.length} color="available" />
                  </section>

                  <section className="panel">
                    <h2>My Overdue Fines</h2>
                    <div className="chart-total">₹{totalFine.toFixed(2)}</div>
                    <p className="chart-caption">Total fines in your borrowing records</p>
                  </section>
                </div>

                <section className="panel">
                  <h2>My Recent Borrowing Records</h2>
                  <LoanTable loans={loans.slice(-5).reverse()} />
                </section>
              </>
            )}

            {page === "Books" && (
              <>
                {isAdmin && (
                  <section className="panel">
                    <h2>Add a New Book</h2>
                    <form className="action-form" onSubmit={addBook}>
                      <label>Title<input required maxLength="150" value={bookForm.title} onChange={e => setBookForm({...bookForm, title: e.target.value})} /></label>
                      <label>Author<input required maxLength="100" value={bookForm.author} onChange={e => setBookForm({...bookForm, author: e.target.value})} /></label>
                      <label>Category<input required value={bookForm.category} onChange={e => setBookForm({...bookForm, category: e.target.value})} /></label>
                      <label>ISBN<input required value={bookForm.isbn} onChange={e => setBookForm({...bookForm, isbn: e.target.value})} /></label>
                      <label>Total copies<input type="number" min="0" required value={bookForm.totalCopies} onChange={e => setBookForm({...bookForm, totalCopies: e.target.value})} /></label>
                      <button type="submit" disabled={busy}>{busy ? "Saving..." : "Add Book"}</button>
                    </form>
                  </section>
                )}

                <section className="panel">
                  <h2>{isAdmin ? "Book Collection" : "Library Catalogue"}</h2>
                  <p className="panel-subtitle">{books.length} titles · {totalCopies} copies</p>
                  <BookTable
                    books={books}
                    onBorrow={!isAdmin ? borrowBookForMember : undefined}
                    busy={busy}
                  />
                </section>
              </>
            )}

            {page === "Members" && isAdmin && (
              <>
                <section className="panel">
                  <h2>Create a Member</h2>
                  <form className="action-form" onSubmit={addMember}>
                    <label>Full name<input required value={memberForm.name} onChange={e => setMemberForm({...memberForm, name: e.target.value})} /></label>
                    <label>Email address<input type="email" required value={memberForm.email} onChange={e => setMemberForm({...memberForm, email: e.target.value})} /></label>
                    <label>Phone number<input type="tel" minLength="10" maxLength="15" required value={memberForm.phone} onChange={e => setMemberForm({...memberForm, phone: e.target.value})} /></label>
                    <button type="submit" disabled={busy}>{busy ? "Saving..." : "Create Member"}</button>
                  </form>
                </section>
                <section className="panel">
                  <h2>Registered Members</h2>
                  <MemberTable members={members} />
                </section>
              </>
            )}

            {page === "Borrowing" && isAdmin && (
              <>
                <section className="panel">
                  <h2>Issue a Book</h2>
                  <form className="action-form" onSubmit={issueBook}>
                    <label>Member
                      <select required value={issueForm.memberId} onChange={e => setIssueForm({...issueForm, memberId: e.target.value})}>
                        <option value="">Select member</option>
                        {members.map(member => (
                          <option key={member.id} value={member.id}>{member.name} — {member.email}</option>
                        ))}
                      </select>
                    </label>
                    <label>Available book
                      <select required value={issueForm.bookId} onChange={e => setIssueForm({...issueForm, bookId: e.target.value})}>
                        <option value="">Select book</option>
                        {books.filter(book => Number(book.availableCopies) > 0).map(book => (
                          <option key={book.id} value={book.id}>{book.title} ({book.availableCopies} available)</option>
                        ))}
                      </select>
                    </label>
                    <button type="submit" disabled={busy || !members.length || !books.some(book => Number(book.availableCopies) > 0)}>
                      {busy ? "Processing..." : "Issue Book"}
                    </button>
                  </form>
                </section>
                <section className="panel">
                  <h2>Borrowing History</h2>
                  <LoanTable loans={loans} onReturn={returnBook} busy={busy} />
                </section>
              </>
            )}

            {page === "My Borrowing" && !isAdmin && (
              <section className="panel">
                <h2>My Borrowing History</h2>
                <p className="panel-subtitle">Only your own borrowing records are displayed.</p>
                <LoanTable loans={loans} />
              </section>
            )}

            {page === "Overdue" && isAdmin && (
              <section className="panel">
                <h2>Overdue Books & Fines</h2>
                <p className="panel-subtitle">Review overdue records and current fines.</p>
                <div className="fine-summary">
                  <span>Total current overdue fines</span>
                  <strong>₹{totalFine.toFixed(2)}</strong>
                </div>
                {overdueLoans.length
                  ? <LoanTable loans={overdueLoans} onReturn={returnBook} busy={busy} />
                  : <p>No overdue loans found.</p>}
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}

function Stat({ title, value, icon }) {
  return (
    <div className="stat-card">
      <div className="stat-top"><p>{title}</p><span className="stat-icon">{icon}</span></div>
      <h2>{value}</h2>
    </div>
  );
}

function Bar({ label, value, total, color }) {
  const percent = total > 0 ? Math.min(100, (value / total) * 100) : 0;
  return (
    <div className="bar-item">
      <div className="bar-label"><span>{label}</span><strong>{value}</strong></div>
      <div className="bar-track">
        <div className={`bar-fill ${color}`} style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}


function BookTable({ books, onBorrow, busy = false }) {
  if (!books.length) return <p>No books found.</p>;

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Author</th>
            <th>Category</th>
            <th>Available</th>
            <th>Total</th>
            {onBorrow && <th>Action</th>}
          </tr>
        </thead>
        <tbody>
          {books.map(book => {
            const available = Number(book.availableCopies || 0);

            return (
              <tr key={book.id}>
                <td>{book.id}</td>
                <td>{book.title}</td>
                <td>{book.author}</td>
                <td>{book.category}</td>
                <td>{available}</td>
                <td>{book.totalCopies}</td>
                {onBorrow && (
                  <td>
                    <button
                      type="button"
                      disabled={busy || available <= 0}
                      onClick={() => onBorrow(book.id)}
                    >
                      {available <= 0
                        ? "Unavailable"
                        : busy
                          ? "Processing..."
                          : "Borrow Book"}
                    </button>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}


function MemberTable({ members }) {
  if (!members.length) return <p>No members registered yet.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Phone</th></tr></thead>
        <tbody>{members.map(member => (
          <tr key={member.id}>
            <td>{member.id}</td><td>{member.name}</td><td>{member.email}</td><td>{member.phone}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

function LoanTable({ loans, onReturn, busy }) {
  if (!loans.length) return <p>No borrowing records found.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Record</th><th>Book</th><th>Member</th><th>Issue Date</th><th>Due Date</th><th>Status</th><th>Fine</th>{onReturn && <th>Action</th>}</tr></thead>

<tbody>
  {loans.map(loan => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const dueDate = loan.dueDate
      ? new Date(`${loan.dueDate}T00:00:00`)
      : null;

    const isOverdue =
      loan.status !== "RETURNED" &&
      dueDate &&
      dueDate < today;

    const daysLeft = dueDate
      ? Math.ceil((dueDate - today) / (1000 * 60 * 60 * 24))
      : null;

    const fine = Number(loan.fineAmount || 0);

    return (
      <tr key={loan.id}>
        <td>{loan.id}</td>
        <td>{loan.book?.title || "—"}</td>
        <td>{loan.member?.name || "You"}</td>
        <td>{loan.issueDate || "—"}</td>
        <td>
          {loan.dueDate || "—"}
          {loan.status !== "RETURNED" && isOverdue && (
            <div className="due-warning">
              Overdue by {Math.abs(daysLeft)} day(s)
            </div>
          )}
          {loan.status !== "RETURNED" &&
            !isOverdue &&
            daysLeft !== null &&
            daysLeft <= 3 && (
              <div className="due-soon">
                {daysLeft === 0
                  ? "Due today"
                  : `Due in ${daysLeft} day(s)`}
              </div>
            )}
        </td>
        <td>
          <span className={`loan-status ${isOverdue ? "overdue" : loan.status?.toLowerCase()}`}>
            {isOverdue ? "OVERDUE" : loan.status}
          </span>
        </td>
        <td className={fine > 0 ? "fine-value" : ""}>
          ₹{fine.toFixed(2)}
          {fine > 0 && <div className="due-warning">Fine pending</div>}
        </td>
        {onReturn && (
          <td>
            {loan.status !== "RETURNED"
              ? <button type="button" disabled={busy} onClick={() => onReturn(loan.id)}>Return</button>
              : "—"}
          </td>
        )}
      </tr>
    );
  })}
</tbody>

      </table>
    </div>
  );
}

export default App;
