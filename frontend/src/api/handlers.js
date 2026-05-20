let logoutHandler = null;
let modalHandler = null;

export const setLogoutHandler = (handler) => {
  logoutHandler = handler;
};

export const setModalHandler = (handler) => {
  modalHandler = handler;
};

export const getLogoutHandler = () => logoutHandler;
export const getModalHandler = () => modalHandler;
